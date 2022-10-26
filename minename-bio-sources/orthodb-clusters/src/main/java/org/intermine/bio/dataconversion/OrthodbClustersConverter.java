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
 * Based on the InterMine Orthodb data Converter
 *
 * @author
 */
public class OrthodbClustersConverter extends BioFileConverter
{
    private static final Logger LOG = Logger.getLogger(OrthodbClustersConverter.class);

    private String dataSourceName = null;
    private String dataSetTitle = null;
    private Item dataSource = null;
    private Item dataSet = null;

    private static final String PROP_FILE = "orthodb-clusters_config.properties";
    private static final String DEFAULT_IDENTIFIER_TYPE = "primaryIdentifier";

    private Set<String> taxonIds = new HashSet<String>();

    private static final String ORTHOLOGUE = "orthologue";
    private static final String PARALOGUE = "paralogue";

    private static final String EVIDENCE_CODE_ABBR = "AA";
    private static final String EVIDENCE_CODE_NAME = "Amino acid sequence comparison";

    private static final int NUM_COLS = 7; // expected minimum number of columns in input file

    private Properties props = new Properties();
    private Map<String, String> config = new HashMap<String, String>();
    private static String evidenceRefId = null;

    private String clusterId = null;
    private String lastCommonAncestor = null;
    private boolean loadOrthoDBClusterIds = false;

    // Keep track of newly created Genes
    private HashMap<String,String> geneIdToRef = new HashMap<String, String>();

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public OrthodbClustersConverter(ItemWriter writer, Model model) {
        super(writer, model);
        readConfig();
    }

    /**
     * Whether to set OrthoDB cluster id as a separate field
     * @param dataSourceName name of datasource for items created
     */
    public void setLoadOrthoDBClusterIds(String loadOrthoDBClusterIds) {
        System.out.println("Setting loadOrthoDBClusterIds to " + loadOrthoDBClusterIds);
        if ("true".equalsIgnoreCase(loadOrthoDBClusterIds)) {
            this.loadOrthoDBClusterIds = true;
        } else {
            this.loadOrthoDBClusterIds = false;
        }
    }

    /**
     * Data source name from project.xml
     * @param dataSourceName name of datasource for items created
     */
    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    /**
     * Data set title from project.xml
     * @param dataSetTitle the title of the DataSets
     */
    public void setDataSetTitle(String dataSetTitle) {
        this.dataSetTitle = dataSetTitle;
    }

    /**
     * Sets the list of taxonIds that should be processed.
     * Only genes/homologues involving these genes will be loaded.
     *
     * @param taxonIds a space-separated list of taxonIds
     */
    public void setOrthodbOrganisms(String taxonIds) {
        this.taxonIds = new HashSet<String>(Arrays.asList(StringUtil.split(taxonIds, " ")));
        System.out.println("Setting list of organisms to " + taxonIds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(Reader reader) throws Exception {
        // Need to keep track of previous cluster ID and last common ancestor since a cluster isn't processed
        // until we get to the next cluster
        String previousClusterId = null;
        String previousLCA = null;
        Set<GeneHolder> genesInCluster = new HashSet<GeneHolder>();

        if (taxonIds.isEmpty()) {
            LOG.warn("orthodb.organisms property not set in project XML file, processing all data");
        }

        Iterator<String[]> lineIter = FormattedTextParser.parseTabDelimitedReader(reader);
        while (lineIter.hasNext()) {
            String[] bits = lineIter.next();
            if (bits.length < NUM_COLS) {
                System.out.println("Row: " + StringUtils.join(bits, ", "));
                throw new RuntimeException("Expected at least " + NUM_COLS + " columns, row has only " + bits.length + " columns.");
            }
            // ignore header (Level is an integer)
            if (bits[0] != null && bits[0].startsWith("OD")) {
                continue;
            }
            clusterId = bits[1];

            // at a different cluster id, process previous homologue cluster
            if (previousClusterId != null && !clusterId.equals(previousClusterId)) {
                // Use previous cluster ID since clusterId now contains cluster ID of next cluster
                // Note lastCommonAncestor hasn't been updated yet so still has previous cluster's value
                processHomologueCluster(previousClusterId, lastCommonAncestor, genesInCluster);
                genesInCluster = new HashSet<GeneHolder>();
            }

            lastCommonAncestor = bits[6]; 
            String taxonId = bits[5];
            if (taxonId != null && isValid(taxonId)) {
                String proteinId = bits[2];
                String geneId = bits[3];
                // protein is default
                String identifier = proteinId;
                if (config.get(taxonId) != null) {
                    identifier = geneId;
                }

                // Create a new GeneHolder for the gene in this row
                GeneHolder gene = new GeneHolder(identifier, taxonId);
                genesInCluster.add(gene);
            }
            previousClusterId = clusterId;
        }
        // parse the last group of the file
        processHomologueCluster(clusterId, lastCommonAncestor, genesInCluster);
    }

    private void readConfig() {
        try {
            props.load(getClass().getClassLoader().getResourceAsStream(
                    PROP_FILE));
        } catch (IOException e) {
            throw new RuntimeException("Problem loading properties '"
                    + PROP_FILE + "'", e);
        }

        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            String key = (String) entry.getKey(); // e.g. 10090.geneid
            String value = ((String) entry.getValue()).trim(); // e.g. symbol

            String[] attributes = key.split("\\.");
            if (attributes.length == 0) {
                throw new RuntimeException("Problem loading properties '" + PROP_FILE
                        + "' on line " + key);
            }
            String taxonId = attributes[0];
            config.put(taxonId, value);
        }
    }

    // Generate all of the n*(n-1) pairs (with symmetry) of the cluster of size n and
    // add to the Homologue table as we go.
    private void processHomologueCluster(String clusterId, String lastCommonAncestor, Set<GeneHolder> genesInCluster)
        throws ObjectStoreException {

        // Check size: if less than 2, nothing to do (no homologue pairs in this cluster)
        if (genesInCluster.size() < 2) {
            return;
        }

        Item cluster = createItem("OrthologueCluster");
        cluster.setAttribute("primaryIdentifier", clusterId);
        cluster.setAttribute("lastCommonAncestor", lastCommonAncestor);
        if (loadOrthoDBClusterIds) {
            cluster.setAttribute("orthoDbCluster", clusterId);
        }
        cluster.addToCollection("dataSets", getDataSet());    

        // Iterate through gene list twice to generate all n*(n-1) pairs and add homologues to database.
        for (GeneHolder gene1 : genesInCluster) {
            String gene1TaxonId = gene1.getTaxonId();
            final String refId1 = getGene(gene1);

            cluster.addToCollection("genes", refId1);

            for (GeneHolder gene2 : genesInCluster) {
                // Exclude identity pairs (don't put a row in the table that a gene is a homologue of itself)
                if (!gene1.equals(gene2)) {
                    // Process homologue pair
                    String gene2TaxonId = gene2.getTaxonId();
                    final String refId2 = getGene(gene2);

                    // The IDs shouldn't be null, nor should they be equal to each other
                    if (refId1 == null || refId2 == null || refId1.equals(refId2)) {
                        System.out.println("******BAD: Skipping due to null value(s) or refId1 = refId2");
                        System.out.println("gene1 id: " + gene1.getIdentifier() + ", gene2 id: " + gene2.getIdentifier()
                                           + ", cluster id: " + clusterId);
                        return;
                    }
                    final String type = (gene1TaxonId.equals(gene2TaxonId) ? PARALOGUE : ORTHOLOGUE);
                    createHomologue(refId1, refId2, type, clusterId, lastCommonAncestor, cluster);
                }
            }
        }

        // Store cluster after all genes have been added to its genes collection
        store(cluster);
    }

    private void createHomologue(String gene1, String gene2, String type, String clusterId, String lastCommonAncestor, Item cluster)
        throws ObjectStoreException {
        Item homologue = createItem("Homologue");
        homologue.setReference("gene", gene1);
        homologue.setReference("homologue", gene2);
        homologue.addToCollection("evidence", getEvidence());
        homologue.setAttribute("type", type);
        homologue.setAttribute("clusterId", clusterId);
        if (loadOrthoDBClusterIds) {
            homologue.setAttribute("orthoDbCluster", clusterId);
        }
        homologue.setAttribute("lastCommonAncestor", lastCommonAncestor);
        homologue.addToCollection("dataSets", getDataSet());
        homologue.setReference("orthologueCluster", cluster);
        store(homologue);
    }

    private Item getDataSet() throws ObjectStoreException {
        if (dataSet == null) {
            dataSet = createItem("DataSet");
            if (StringUtils.isEmpty(dataSetTitle)) {
                throw new RuntimeException("Data set title not set in project.xml");
            }
            dataSet.setAttribute("name", dataSetTitle);
            dataSet.setReference("dataSource", getDataSourceRefId());
            try {
                store(dataSet);
            } catch (ObjectStoreException e) {
                throw new RuntimeException("failed to store DataSet with name: " + dataSetTitle, e);
            }
        }
        return dataSet;
    }

    private String getDataSourceRefId() throws ObjectStoreException {
        if (dataSource == null) {
            dataSource = createItem("DataSource");
            if (StringUtils.isEmpty(dataSourceName)) {
                throw new RuntimeException("Data source name not set in project.xml");
            }
            dataSource.setAttribute("name", dataSourceName);
            try {
                store(dataSource);
            } catch (ObjectStoreException e) {
                throw new RuntimeException("failed to store DataSource with name: " + dataSourceName, e);
            }
        }
        return dataSource.getIdentifier();
    }

    // genes are only processed if they are of an organism of interest
    private boolean isValid(String taxonId) {
        if (taxonIds.isEmpty() || taxonIds.contains(taxonId)) {
            // either this is an organism of interest or we are processing everything
            return true;
        }
        return false;
    }

    // Creates the Gene objects referred to in the Homologue table.
    // The refIds keep track of whether a Gene object has been created for a gene identifier
    // so that we don't create duplicates.
    private String getGene(GeneHolder holder) throws ObjectStoreException {
        String geneId = holder.getIdentifier();
        String refId = geneIdToRef.get(geneId);

        if (refId == null) {
            // Haven't created Gene yet, create it now and store ref
            String taxonId = holder.getTaxonId();
            String identiferType = config.get(taxonId);
            if (StringUtils.isEmpty(identiferType)) {
                identiferType = DEFAULT_IDENTIFIER_TYPE;
            }
            Item gene = createItem("Gene");
            gene.setAttribute(identiferType, geneId);
            gene.setReference("organism", getOrganism(taxonId));
            gene.addToCollection("dataSets", getDataSet());
            refId = gene.getIdentifier();
            geneIdToRef.put(geneId, refId);
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

    // Class that keeps track of gene info from one row of input file
    private class GeneHolder
    {
        private String identifier;          // gene identifier
        private String taxonId;             // gene taxon ID

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

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof GeneHolder) {
                GeneHolder gh = (GeneHolder) obj;
                return identifier.equals(gh.identifier)
                    && taxonId.equals(gh.taxonId);
            }
            return false;
        }

        @Override
        public String toString() {
            return "(" + identifier + ", " + taxonId + ")";
        }
    }
}
