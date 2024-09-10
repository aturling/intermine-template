package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2024 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.collections.keyvalue.MultiKey;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.metadata.Model;
import org.intermine.metadata.StringUtil;
import org.intermine.xml.full.Item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * GplusE orthologs
 * modeled after OrthoDB converter 
 * @author
 */
public class GpluseOrthologsConverter extends BioFileConverter
{
    private static final Logger LOG = Logger.getLogger(GpluseOrthologsConverter.class);

    private static final String DATASET_TITLE = "GplusE orthologs data set";
    private static final String DATA_SOURCE_NAME = "GplusE";

    private Set<String> taxonIds = new HashSet<String>();
    private Set<String> homologueTaxonIds = new HashSet<String>();
    private static String evidenceRefId = null;

    private static final String ORTHOLOGUE = "orthologue";
    private static final String PARALOGUE = "paralogue";

    private static final String EVIDENCE_CODE_ABBR = "AA";
    private static final String EVIDENCE_CODE_NAME = "Amino acid sequence comparison";

    private Map<GeneHolder, List<GeneHolder>> geneToHomologues = new LinkedHashMap<GeneHolder,
            List<GeneHolder>>();
    private Map<MultiKey, GeneHolder> identifierToGene = new LinkedHashMap<MultiKey, GeneHolder>();

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public GpluseOrthologsConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * Sets the list of taxonIds that should be processed.  All genes will be loaded.
     *
     * @param taxonIds a space-separated list of taxonIds
     */
    public void setGpluseorthologsOrganisms(String taxonIds) {
        this.taxonIds = new HashSet<String>(Arrays.asList(StringUtil.split(taxonIds, " ")));
        LOG.info("Setting list of organisms to " + taxonIds);
    }

    /**
     * Sets the list of taxonIds of homologues that should be processed.  These homologues will only
     * be processed if they are homologues for the organisms of interest.
     *
     * @param homologues a space-separated list of taxonIds
     */
    public void setGpluseorthologsHomologues(String homologues) {
        this.homologueTaxonIds = new HashSet<String>(Arrays.asList(
                StringUtil.split(homologues, " ")));
        LOG.info("Setting list of homologues to " + homologues);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        String previousGroup = null;
        List<GeneHolder> homologues = new ArrayList<GeneHolder>();

        if (taxonIds.isEmpty()) {
            LOG.warn("gpluseorthologs.organisms property not set in project XML file, processing all data");
        }

        Iterator<String[]> lineIter = FormattedTextParser.parseTabDelimitedReader(reader);
        while (lineIter.hasNext()) {
            String[] bits = lineIter.next();
            if (bits.length < 9) {
                continue;
            }

            // Assuming no header
            String groupId = bits[1];

            // at a different groupId, process previous homologue group
            if (previousGroup != null && !groupId.equals(previousGroup)) {
                processHomologueGroup(homologues);
                homologues = new ArrayList<GeneHolder>();
            }

            String taxonId = bits[5];

            if (taxonId != null) {
                String geneId = bits[3];
                MultiKey key = new MultiKey(geneId, taxonId);
                GeneHolder gene = identifierToGene.get(key);
                if (gene == null) {
                    gene = new GeneHolder(geneId, taxonId);
                    identifierToGene.put(key, gene);
                }
                homologues.add(gene);
            }
            previousGroup = groupId;
        }
        // parse the last group of the file
        processHomologueGroup(homologues);

        // store genes, set relationships
        processHomologues();
    }

    private void processHomologuePair(GeneHolder gene, GeneHolder homologue)
        throws ObjectStoreException {

        String geneTaxonId = gene.getTaxonId();
        String homologueTaxonId = homologue.getTaxonId();

        // at least one of these pair must be from an organism of interest
        if (!isValidPair(geneTaxonId, homologueTaxonId)) {
            return;
        }

        final String refId1 = getGene(gene);
        final String refId2 = getGene(homologue);

        if (refId1 == null || refId2 == null || refId1.equals(refId2)) {
            return;
        }
        final String type = (geneTaxonId.equals(homologueTaxonId) ? PARALOGUE : ORTHOLOGUE);
        createHomologue(refId1, refId2, type);
    }

    private void processHomologues() throws ObjectStoreException {
        for (Entry<GeneHolder, List<GeneHolder>> entry : geneToHomologues.entrySet()) {
            GeneHolder gene = entry.getKey();
            List<GeneHolder> homologues = entry.getValue();
            for (GeneHolder homologue : homologues) {
                processHomologuePair(gene, homologue);
            }
        }
    }

    // create maps from all homologues to all other homologues. need to keep in maps to prevent
    // dupes
    private void processHomologueGroup(List<GeneHolder> homologueList) {
        for (GeneHolder geneHolder : homologueList) {
            List<GeneHolder> homologues = new ArrayList(homologueList);
            List<GeneHolder> previousHomologues = geneToHomologues.get(geneHolder);
            if (previousHomologues != null && previousHomologues.size() > 0) {
                homologues.addAll(previousHomologues);
            }
            geneToHomologues.put(geneHolder, homologues);
        }
    }

    private void createHomologue(String gene1, String gene2, String type)
        throws ObjectStoreException {
        Item homologue = createItem("Homologue");
        homologue.setReference("gene", gene1);
        homologue.setReference("homologue", gene2);
        homologue.addToCollection("evidence", getEvidence());
        homologue.setAttribute("type", type);
        homologue.setAttribute("source", DATA_SOURCE_NAME);
        store(homologue);
    }

    // genes (in taxonIDs) are always processed
    // homologues are only processed if they are of an organism of interest
    private boolean isValid(String taxonId) {
        if (taxonIds.isEmpty() || taxonIds.contains(taxonId)) {
            // either this is an organism of interest or we are processing everything
            return true;
        }
        if (homologueTaxonIds.isEmpty()) {
            // no config for homologues. since this taxon has failed the previous test, it's
            // not an organism of interest
            return false;
        }
        if (homologueTaxonIds.contains(taxonId)) {
            // in config, so we want it
            return true;
        }
        // not found in config
        return false;
    }

    // genes (in taxonIDs) are always processed
    // homologues are only processed if they are of an organism of interest
    private boolean isValidPair(String geneTaxonId, String homologueTaxonId) {
        if (taxonIds.isEmpty()) {
            // we are processing everything
            return true;
        }
        if (taxonIds.contains(geneTaxonId) && taxonIds.contains(homologueTaxonId)) {
            // both genes are valid
            return true;
        }
        if (!taxonIds.contains(geneTaxonId) && !taxonIds.contains(homologueTaxonId)) {
            // neither genes are valid
            return false;
        }
        if (homologueTaxonIds.contains(geneTaxonId)
                || homologueTaxonIds.contains(homologueTaxonId)) {
            // at least one of the genes is valid (because it passed the last test)
            // and one gene is in the list of homologues
            return true;
        }
        return false;
    }

    private String getGene(GeneHolder holder) throws ObjectStoreException {
        String refId = holder.getRefId();
        if (refId == null) {
            String taxonId = holder.getTaxonId();
            Item gene = createItem("Gene");
            gene.setAttribute("primaryIdentifier", holder.getIdentifier());
            gene.setReference("organism", getOrganism(taxonId));
            refId = gene.getIdentifier();
            holder.setRefId(refId);
            store(gene);
        }
        return refId;
    }

    private String getEvidence() throws ObjectStoreException {
        if (evidenceRefId == null) {
            Item item = createItem("OrthologueEvidenceCode");
            item.setAttribute("abbreviation", EVIDENCE_CODE_ABBR);
            item.setAttribute("name", EVIDENCE_CODE_NAME);
            try {
                store(item);
            } catch (ObjectStoreException e) {
                throw new ObjectStoreException(e);
            }
            String refId = item.getIdentifier();

            item = createItem("OrthologueEvidence");
            item.setReference("evidenceCode", refId);
            try {
                store(item);
            } catch (ObjectStoreException e) {
                throw new ObjectStoreException(e);
            }

            evidenceRefId = item.getIdentifier();
        }
        return evidenceRefId;
    }

    private class GeneHolder
    {
        private String identifier;
        private String taxonId;
        private String refId;

        protected GeneHolder(String identifier, String taxonId) {
            this.identifier = identifier;
            this.taxonId = taxonId;
        }

        protected String getTaxonId() {
            return taxonId;
        }

        protected String getIdentifier() {
            return identifier;
        }

        protected String getRefId() {
            return refId;
        }

        protected void setRefId(String refId) {
            this.refId = refId;
        }
    }
}
