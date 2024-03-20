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
import org.intermine.metadata.TypeUtil;
import org.intermine.model.bio.BioEntity;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.xml.full.Item;
import org.intermine.util.FormattedTextParser;

/**
 * A Converter that loads cross references, across data sets, for BioEntity (e.g., Gene) entity. 
 * @author
 */
public class CrossReferencesConverter extends BioFileConverter
{
    private static final Logger LOG = Logger.getLogger(CrossReferencesConverter.class);
    protected Map<String, Item> itemMap = new HashMap<String, Item>();
    private String className = "Gene";             // e.g., "Gene" or "org.intermine.model.bio.Gene"
    private String unqualifiedClassName = "Gene";  // e.g., "Gene"
    private String dataSourceName = null;
    private String dataSetTitle = null;
    private Item dataSource = null;
    private Item dataSet = null;
    private String taxonId = null;
    private String organismReferenceId = null;

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public CrossReferencesConverter(ItemWriter writer, Model model) {
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
     * The class name to use for objects created during load.
     * Default is Gene
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
        // Check class name
        Class<?> cls = getModel().getClassDescriptorByName(className).getType();
        if (!BioEntity.class.isAssignableFrom(cls)) {
            throw new RuntimeException("className must be a valid class in the model"
                        + " that extends BioEntity, but was: " + className);
        }
        // Need unqualified class name to use createItem()
        unqualifiedClassName = TypeUtil.unqualifiedName(className);

        File currentFile = getCurrentFile();
        organismReferenceId = getOrganism(taxonId);
        Iterator<String[]> lineIter = FormattedTextParser.parseTabDelimitedReader(reader);

        while (lineIter.hasNext()) {
            String[] line = lineIter.next();
            String[] subjectInfo = line[0].trim().split(":");
            String subjectPrimaryIdentifier = subjectInfo[0];
            String subjectSource = subjectInfo[1];

            Item item1 = getBioEntity(subjectPrimaryIdentifier, subjectSource);

            // Load type, if present
            String type = null;
            if (line.length > 2) {
                type = line[2];
            }
 
            String[] targetEntryList = line[1].trim().split(",");
            for (String targetEntry : targetEntryList) {
                String[] targetInfo = targetEntry.split(":");
                String targetPrimaryIdentifier = targetInfo[0];
                String targetSource = targetInfo[1];
                //LOG.info("Subject: " + subjectPrimaryIdentifier + " <> Target: " + targetPrimaryIdentifier);

                Item item2 = getBioEntity(targetPrimaryIdentifier, targetSource);

                // create xref linking item1 (as subject) and item2 (as target)
                Item xrefItem = createItem("CrossReference");
                xrefItem.setAttribute("identifier", targetPrimaryIdentifier);
                xrefItem.setReference("source", getDataSourceRefId()); // DataSource
                xrefItem.setReference("subject", item1.getIdentifier());
                xrefItem.setReference("target", item2.getIdentifier());
                if (type != null) {
                    xrefItem.setAttribute("type", type);
                }
                storeItem(xrefItem);
            }
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

    private Item getBioEntity(String identifier, String source) 
        throws ObjectStoreException {
        Item bioItem = null;
        if (itemMap.containsKey(identifier)) {
            bioItem = itemMap.get(identifier);
        } else {
            bioItem = createItem(unqualifiedClassName);  // extends BioEntity
            bioItem.setAttribute("primaryIdentifier", identifier);
            bioItem.setAttribute("source", source);
            bioItem.setReference("organism", organismReferenceId);
            bioItem.addToCollection("dataSets", getDataSet());
            itemMap.put(identifier, bioItem);
        }
        return bioItem;
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
        storeAllItems(itemMap);
    }
}
