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

import java.io.File;
import java.io.Reader;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;
import org.intermine.util.FormattedTextParser;

/**
 * A Converter that loads cross references, across gene sets, for Gene entity. 
 * @author
 */
public class CrossReferencesConverter extends BioFileConverter
{
    private static final Logger LOG = Logger.getLogger(CrossReferencesConverter.class);
    private static final String DATASET_TITLE = "Gene ID Cross References (Ensembl <=> RefSeq) data set";
    private static final String DATA_SOURCE_NAME = "AquaMine";
    protected Map<String, Item> geneItemMap = new HashMap<String, Item>();
    protected Map<String, Item> dataSourceItemMap = new HashMap<String, Item>();
    private String taxonId = null;
    private String organismReferenceId = null;

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public CrossReferencesConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * Set the organism's taxon ID.
     * @param taxonId
     */
    public void setTaxonId(String taxonId) {
        this.taxonId = taxonId;
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        File currentFile = getCurrentFile();
        organismReferenceId = getOrganism(taxonId);
        Iterator<String[]> lineIter = FormattedTextParser.parseTabDelimitedReader(reader);

        while (lineIter.hasNext()) {
            String[] line = lineIter.next();
            String[] subjectInfo = line[0].trim().split(":");
            String subjectPrimaryIdentifier = subjectInfo[0];
            String subjectSource = subjectInfo[1];

            Item geneItem1 = getGene(subjectPrimaryIdentifier, subjectSource);
 
            String[] targetEntryList = line[1].trim().split(",");
            for (String targetEntry : targetEntryList) {
                String[] targetInfo = targetEntry.split(":");
                String targetPrimaryIdentifier = targetInfo[0];
                String targetSource = targetInfo[1];
                LOG.info("Subject: " + subjectPrimaryIdentifier + " <> Target: " + targetPrimaryIdentifier);

                Item geneItem2 = getGene(targetPrimaryIdentifier, targetSource);

                // create xref linking gene1 (as subject) and gene2 (as target)
                Item xrefItem = createItem("CrossReference");
                xrefItem.setAttribute("identifier", targetPrimaryIdentifier);
                xrefItem.setReference("source", getDataSourceRefId(targetSource));
                xrefItem.setReference("subject", geneItem1.getIdentifier());
                xrefItem.setReference("target", geneItem2.getIdentifier());
                storeItem(xrefItem);
            }
        }
    }

    /**
     * Store a given item
     * @param item
     */
    protected void storeItem(Item item) {
        try {
            store(item);
        } catch (Exception e) {
            System.out.println("Error while storing item: " + item);
            System.out.println("Exception stacktrace: " + e);
        }
    }

    protected Item getGene(String primaryIdentifier, String source) {
        Item geneItem = null;
        if (geneItemMap.containsKey(primaryIdentifier)) {
            geneItem = geneItemMap.get(primaryIdentifier);
        } else {
            geneItem = createItem("Gene");
            geneItem.setAttribute("primaryIdentifier", primaryIdentifier);
            geneItem.setAttribute("source", source);
            geneItem.setReference("organism", organismReferenceId);
            geneItemMap.put(primaryIdentifier, geneItem);
        }
        return geneItem;
    }

    protected String getDataSourceRefId(String dataSourceName) {
        Item dataSourceItem = null;
        if (dataSourceItemMap.containsKey(dataSourceName)) {
            dataSourceItem = dataSourceItemMap.get(dataSourceName);
        } else {
            dataSourceItem = createItem("DataSource");
            dataSourceItem.setAttribute("name", dataSourceName);
            dataSourceItemMap.put(dataSourceName, dataSourceItem);
        }
        return dataSourceItem.getIdentifier();
    }

    /**
     * Store all items in a given Map
     * @param itemMap
     */
    protected void storeAllItems(Map<String, Item> itemMap) {
        for (String key : itemMap.keySet()) {
            storeItem(itemMap.get(key));
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception {
        storeAllItems(dataSourceItemMap);
        storeAllItems(geneItemMap);
    }
}
