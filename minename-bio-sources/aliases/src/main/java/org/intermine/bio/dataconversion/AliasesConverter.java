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
    Map<String, Item> ftItemMap = new HashMap<String, Item>();
    Map<String, Item> aliasItemMap = new HashMap<String, Item>();
    Map<String, Item> dataSourceItemMap = new HashMap<String, Item>();
    Map<String, Item> dataSetItemMap = new HashMap<String, Item>();
    private String className = null;             // e.g., "Gene" or "org.intermine.model.bio.Gene"
    private String unqualifiedClassName = null;  // e.g., "Gene"
    private String taxonId = null;
    String organismReferenceId = null;

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
                // Add to maps after creating and setting refs + collections
                aliasItemMap.put(aliasEntry, aliasItem);
                ftItemMap.put(ftPrimaryIdentifier, ftItem);
            }
        }
    }

    protected Item getFeatureItem(String ftPrimaryIdentifier, String ftSource) {
        Item ftItem;

        if (ftItemMap.containsKey(ftPrimaryIdentifier)) {
            ftItem = ftItemMap.get(ftPrimaryIdentifier);
        } else {
            ftItem = createItem(unqualifiedClassName);
            ftItem.setAttribute("primaryIdentifier", ftPrimaryIdentifier);
            ftItem.setAttribute("source", ftSource);
            ftItem.setReference("organism", organismReferenceId);
            ftItemMap.put(ftPrimaryIdentifier, ftItem);
        }
        return ftItem;
    }

    protected Item getAliasItem(String aliasEntry) {
        Item aliasItem;

        String[] aliasInfo = aliasEntry.split(":");
        String str = aliasInfo[0];
        String info = str.replaceAll("@", ":");
        String aliasIdentifier = info;
        String aliasSource = aliasInfo[1];

        Item dataSourceItem = getDataSourceItem(aliasSource);
        Item dataSetItem = getDataSetItem(aliasSource, dataSourceItem);

        if (aliasItemMap.containsKey(aliasEntry)) {
            aliasItem = aliasItemMap.get(aliasEntry);
        } else {
            aliasItem = createItem("AliasName");
            aliasItem.setAttribute("identifier", aliasIdentifier);
            aliasItem.setAttribute("source", aliasSource);
            aliasItem.setReference("organism", organismReferenceId);
            aliasItem.addToCollection("dataSets", dataSetItem);
        }
        return aliasItem;
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
            System.out.println("Exception: " + e);
        }
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
     * Get or create an item for a given dataSourceName
     * @param dataSourceName
     * @return
     */
    protected Item getDataSourceItem(String dataSourceName) {
        Item dataSourceItem = null;
        if (dataSourceName != null) {
            if (dataSourceItemMap.containsKey(dataSourceName)) {
                dataSourceItem = dataSourceItemMap.get(dataSourceName);
            } else {
                dataSourceItem = createItem("DataSource");
                dataSourceItem.setAttribute("name", dataSourceName);
                dataSourceItemMap.put(dataSourceName, dataSourceItem);
            }
        }
        return dataSourceItem;
    }

    /**
     * Get or create an item for a given dataSetName
     * @param dataSetName
     * @param dataSourceItem
     * @return
     */
    protected Item getDataSetItem(String dataSetName, Item dataSourceItem) {
        Item dataSetItem = null;
        if (dataSetName != null) {
            if (dataSetItemMap.containsKey(dataSetName)) {
                dataSetItem = dataSetItemMap.get(dataSetName);
            } else {
                dataSetItem = createItem("DataSet");
                dataSetItem.setAttribute("name", dataSetName);
                dataSetItem.setReference("dataSource", dataSourceItem.getIdentifier());
                dataSetItemMap.put(dataSetName, dataSetItem);
            }
        }
        return dataSetItem;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception {
        storeAllItems(dataSourceItemMap);
        storeAllItems(dataSetItemMap);
        storeAllItems(aliasItemMap);
        storeAllItems(ftItemMap);
    }
}

