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

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;
import org.apache.log4j.Logger;
import org.apache.commons.lang.StringUtils;
import org.intermine.metadata.TypeUtil;
import org.intermine.model.bio.DataSet;
import org.intermine.model.bio.Organism;
import org.intermine.model.bio.SequenceFeature;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;

import org.intermine.xml.full.Reference;
import org.intermine.xml.full.ReferenceList;
import java.util.Collection;
import org.intermine.xml.full.Attribute;

/**
 * A Converter that loads aliases.
 * @author
 */
public class AliasesConverter extends BioFileConverter
{
    private Map<String, Item> ftItemMap = new HashMap<String, Item>();
    private Map<String, Item> aliasItemMap = new HashMap<String, Item>();
    private String className = null;             // e.g., "Gene" or "org.intermine.model.bio.Gene"
    private String unqualifiedClassName = null;  // e.g., "Gene"
    private String taxonId = null;
    private String dataSourceName = null;
    private String dataSetTitle = null;
    private Item dataSource = null;
    private Item dataSet = null;
    private String organismReferenceId = null;

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public AliasesConverter(ItemWriter writer, Model model) {
        super(writer, model);
    }

    /**
     * Set the organism's taxon ID.
     * @param taxonId
     */
    public void setTaxonId(String taxonId) {
        this.taxonId = taxonId;
    }

    /**
     * The class name to use for objects created during load.  Generally this is
     * "org.intermine.model.bio.Gene" or "org.intermine.model.bio.Transcript"
     * @param className the class name
     */
    public void setClassName(String className) {
        this.className = className;
    }

    /**
     * Set the data source name.
     * @param dataSourceName name of datasource for items created
     */
    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    /**
     * Set the data set title.
     * @param dataSetTitle the title of the DataSets
     */
    public void setDataSetTitle(String dataSetTitle) {
        this.dataSetTitle = dataSetTitle;
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        if (taxonId == null) {
            throw new RuntimeException("taxonId needs to be set");
        }
        if (className == null) {
            throw new RuntimeException("className needs to be set");
        }

        Class<?> cls = getModel().getClassDescriptorByName(className).getType();
        if (!SequenceFeature.class.isAssignableFrom(cls)) {
            throw new RuntimeException("className must be a valid class in the model"
                        + " that extends SequenceFeature, but was: " + className);
        }
        // Need unqualified class name to use createItem()
        unqualifiedClassName = TypeUtil.unqualifiedName(className);
        organismReferenceId = getOrganism(taxonId);
        if (organismReferenceId == null) {
            throw new RuntimeException("invalid taxon ID: " + taxonId);
        }

        File currentFile = getCurrentFile();
        Iterator<String[]> lineIter = FormattedTextParser.parseTabDelimitedReader(reader);

        while (lineIter.hasNext()) {
            String[] line = lineIter.next();
            String[] ftInfo = line[0].trim().split(":");
            String ftPrimaryIdentifier = ftInfo[0];
            String ftSource = ftInfo[1];

            Item ftItem = getFeatureItem(ftPrimaryIdentifier, ftSource);

            String[] aliasEntryList = line[1].trim().split(",");
            for (String aliasEntry : aliasEntryList) {
                Item aliasItem = getAliasItem(aliasEntry);
                aliasItem.addToCollection("features", ftItem.getIdentifier());
            }
        }
    }

    protected Item getFeatureItem(String ftPrimaryIdentifier, String ftSource) 
        throws ObjectStoreException {
        Item ftItem;

        if (ftItemMap.containsKey(ftPrimaryIdentifier)) {
            ftItem = ftItemMap.get(ftPrimaryIdentifier);
        } else {
            ftItem = createItem(unqualifiedClassName); // extends SequenceFeature
            ftItem.setAttribute("primaryIdentifier", ftPrimaryIdentifier);
            ftItem.setAttribute("source", ftSource);
            ftItem.setReference("organism", organismReferenceId);
            ftItem.addToCollection("dataSets", getDataSet());
            ftItemMap.put(ftPrimaryIdentifier, ftItem);
        }
        return ftItem;
    }

    protected Item getAliasItem(String aliasEntry) throws ObjectStoreException {
        Item aliasItem;

        String[] aliasInfo = aliasEntry.split(":");
        String str = aliasInfo[0];
        String info = str.replaceAll("@", ":");
        String aliasIdentifier = info;
        String aliasSource = aliasInfo[1];

        if (aliasItemMap.containsKey(aliasEntry)) {
            aliasItem = aliasItemMap.get(aliasEntry);
        } else {
            aliasItem = createItem("AliasName");
            aliasItem.setAttribute("identifier", aliasIdentifier);
            aliasItem.setAttribute("source", aliasSource);
            aliasItem.setReference("organism", organismReferenceId);
            aliasItem.addToCollection("dataSets", getDataSet());
            aliasItemMap.put(aliasEntry, aliasItem);
        }
        return aliasItem;
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

    /**
     * Store all items in a given Map
     * @param itemMap
     */
    protected void storeAllItems(Map<String, Item> itemMap) throws ObjectStoreException {
        for (String key : itemMap.keySet()) {
            storeItem(itemMap.get(key));
        }
    }

    private String getDataSourceRefId() {
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

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception {
        storeAllItems(aliasItemMap);
        storeAllItems(ftItemMap);
    }
}

