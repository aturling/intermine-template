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
import org.apache.commons.lang.StringUtils;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.xml.full.Item;
import org.intermine.util.FormattedTextParser;

/**
 * A Converter that loads reciprocal best hits for genes. 
 * @author
 */
public class ReciprocalBestHitsConverter extends BioFileConverter
{
    private String dataSourceName = null;
    private String dataSetTitle = null;
    private Item dataSetItem = null;
    private Item dataSourceItem = null;

    private static final Logger LOG = Logger.getLogger(ReciprocalBestHitsConverter.class);
    protected Map<String, Item> geneItemMap = new HashMap<String, Item>();

    /**
     * Sets the data set title.
     *
     * @param dataSetTitle data set title
     */
    public void setDataSetTitle(String dataSetTitle) {
        this.dataSetTitle = dataSetTitle;
    }

    /**
     * Sets the data source name.
     *
     * @param dataSourceName data source name
     */
    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public ReciprocalBestHitsConverter(ItemWriter writer, Model model) {
        super(writer, model);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        File currentFile = getCurrentFile();
        Iterator<String[]> lineIter = FormattedTextParser.parseTabDelimitedReader(reader);

        while (lineIter.hasNext()) {
            // Line format is: GeneID:TaxonID	RBHGeneID:RBHTaxonID
            String[] line = lineIter.next();
            String[] subjectInfo = line[0].trim().split(":");
            String subjectPrimaryIdentifier = subjectInfo[0];
            String subjectTaxonId = subjectInfo[1];

            Item geneItem1 = getGene(subjectPrimaryIdentifier, subjectTaxonId);
 
            String[] rbhInfo = line[1].trim().split(":");
            String rbhPrimaryIdentifier = rbhInfo[0];
            String rbhTaxonId = rbhInfo[1];
            LOG.info("Subject: " + subjectPrimaryIdentifier + " <> RBH: " + rbhPrimaryIdentifier);

            Item geneItem2 = getGene(rbhPrimaryIdentifier, rbhTaxonId);

            // create RBH linking gene1 (as subject) and gene2 (as rbh)
            Item rbhItem = createItem("ReciprocalBestHit");
            rbhItem.setAttribute("identifier", rbhPrimaryIdentifier);
            rbhItem.setReference("subject", geneItem1.getIdentifier());
            rbhItem.setReference("reciprocalBestHit", geneItem2.getIdentifier());
            rbhItem.addToCollection("dataSets", getDataSet());
            storeItem(rbhItem);
        }
    }

    /**
     * Store a given item
     * @param item
     */
    protected void storeItem(Item item) throws ObjectStoreException {
        try {
            store(item);
        } catch (ObjectStoreException e) {
            throw new RuntimeException("Error while storing item: " + item, e);
        }
    }

    private Item getGene(String primaryIdentifier, String taxonId) 
        throws ObjectStoreException {
        Item geneItem = null;
        if (geneItemMap.containsKey(primaryIdentifier)) {
            geneItem = geneItemMap.get(primaryIdentifier);
        } else {
            geneItem = createItem("Gene");
            geneItem.setAttribute("primaryIdentifier", primaryIdentifier);
            geneItem.setReference("organism", getOrganism(taxonId));
            geneItem.addToCollection("dataSets", getDataSet());
            geneItemMap.put(primaryIdentifier, geneItem);
        }
        return geneItem;
    }

    /**
     * Returns the current DataSource item
     * @return
     * @throws ObjectStoreException
     */
    private Item getDataSource() throws ObjectStoreException {
        if (dataSourceItem == null) {
            if (StringUtils.isEmpty(dataSourceName)) {
                throw new RuntimeException("Data source name not set in project.xml");
            }
            dataSourceItem = createItem("DataSource");
            dataSourceItem.setAttribute("name", dataSourceName);
            storeItem(dataSourceItem);
        }
        return dataSourceItem;
    }

    /**
     * Returns the current DataSet item
     * @return
     * @throws ObjectStoreException
     */
    private Item getDataSet() throws ObjectStoreException {
        if (dataSetItem == null) {
            if (StringUtils.isEmpty(dataSetTitle)) {
                throw new RuntimeException("Data set title not set in project.xml");
            }
            dataSetItem = createItem("DataSet"); 
            dataSetItem.setAttribute("name", dataSetTitle);
            dataSetItem.setReference("dataSource", getDataSource());
            storeItem(dataSetItem);
        }
        return dataSetItem;
    }

    /**
     * Store all items in a given Map
     * @param itemMap
     */
    protected void storeAllItems(Map<String, Item> itemMap) throws ObjectStoreException {
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
        storeAllItems(geneItemMap);
    }
}
