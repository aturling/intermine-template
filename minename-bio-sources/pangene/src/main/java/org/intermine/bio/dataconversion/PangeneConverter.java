package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2022 FlyMine
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
import java.util.Properties;
import java.util.Set;

import org.apache.commons.collections.keyvalue.MultiKey;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.metadata.StringUtil;
import org.intermine.xml.full.Item;

/**
 * 
 * @author
 */
public class PangeneConverter extends BioFileConverter
{
    private static final String DATASET_TITLE = "MaizeGDB-NAM-Pangene data set";
    private static final String DATA_SOURCE_NAME = "MaizeGDB";
    private static final int NUM_COLS = 9; // expected number of columns in input file

    private String groupId = null;

    // Keep track of newly created Genes
    private HashMap<String,String> geneIdToRef = new HashMap<String, String>();

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public PangeneConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        // Need to keep track of previous group ID since a group isn't processed
        // until we get to the next group
        String previousGroupId = null;
        Set<String> geneIdsInGroup = new HashSet<String>();

        Iterator<String[]> lineIter = FormattedTextParser.parseTabDelimitedReader(reader);
        while (lineIter.hasNext()) {
            String[] bits = lineIter.next();
            if (bits.length < NUM_COLS) {
                throw new RuntimeException("Expected " + NUM_COLS + " columns, row has only " + bits.length + " columns.");
            }
            groupId = bits[1];

            // at a different group ID, process previous group
            if (previousGroupId != null && !groupId.equals(previousGroupId)) {
                // Use previous group ID since groupId now contains ID of next group
                processPangeneGroup(previousGroupId, geneIdsInGroup);
                geneIdsInGroup = new HashSet<String>();
            }

            String taxonId = bits[5];
            String geneId = bits[3];

            // Create a new gene for the gene ID in this row
            String refId = getGene(geneId, taxonId);
            geneIdsInGroup.add(geneId);
            previousGroupId = groupId;
        }
        // parse the last group of the file
        processPangeneGroup(groupId, geneIdsInGroup);
    }

    // Generate all of the n*(n-1) pairs (with symmetry) of the group of size n and
    // add to the Syntelog table as we go.
    private void processPangeneGroup(String groupId, Set<String> geneIdsInGroup)
        throws ObjectStoreException {

        // Check size: if less than 1, nothing to do (no syntelogs in this cluster)
        if (geneIdsInGroup.size() < 1) {
            return;
        }

        // Add group to PangeneGroup
        Item group = createItem("PangeneGroup");
        group.setAttribute("primaryIdentifier", groupId);

        // Iterate through gene list twice to generate all n*(n-1) pairs and add syntelogs to database.
        for (String geneId1 : geneIdsInGroup) {
            final String refId1 = geneIdToRef.get(geneId1);
            group.addToCollection("genes", refId1);

            for (String geneId2 : geneIdsInGroup) {
                final String refId2 = geneIdToRef.get(geneId2);
                // Exclude identity pairs
                if (!geneId1.equals(geneId2)) {
                    // Process pair
                    createSyntelog(refId1, refId2, groupId, group);
                }
            }
        }

        // Store group after all genes have been added to its genes collection
        store(group);
    }

    private void createSyntelog(String gene1, String gene2, String groupId, Item group)
        throws ObjectStoreException {
        Item syntelog = createItem("Syntelog");
        syntelog.setReference("gene", gene1);
        syntelog.setReference("syntelog", gene2);
        syntelog.setAttribute("pangeneId", groupId);
        syntelog.setReference("pangeneGroup", group);
        store(syntelog);
    }

    private String getGene(String geneId, String taxonId) throws ObjectStoreException {
        String refId = geneIdToRef.get(geneId);

        if (refId == null) {
            // Haven't created Gene yet, create it now and store ref
            Item gene = createItem("Gene");
            gene.setAttribute("primaryIdentifier", geneId);
            gene.setReference("organism", getOrganism(taxonId));
            refId = gene.getIdentifier();
            geneIdToRef.put(geneId, refId);
            store(gene);
        }
        return refId;
    }
}
