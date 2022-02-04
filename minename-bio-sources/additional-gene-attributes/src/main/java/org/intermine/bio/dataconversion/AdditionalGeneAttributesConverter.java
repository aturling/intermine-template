package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2021 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.Reader;
import java.lang.Exception;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.metadata.StringUtil;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;


/**
 * 
 * @author
 */
public class AdditionalGeneAttributesConverter extends BioFileConverter
{
    protected static final Logger LOG = Logger.getLogger(AdditionalGeneAttributesConverter.class);
    private String dataSourceName, dataSetTitle, attributeName;
    private Item dataSource, dataSet;
    //String taxonId;
    //String organismReferenceId;

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public AdditionalGeneAttributesConverter(ItemWriter writer, Model model) {
        super(writer, model);
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
     * Gene attribute name from project.xml
     * @param attributeName the gene attribute to set
     */
    public void setAttributeName(String attributeName) {
        // Convert to lowercase
        this.attributeName = attributeName.toLowerCase();
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        if (attributeName.isEmpty()) {
            throw new RuntimeException("Attribute name cannot be empty");
        }

        // assumes that the metadata file has unique entries
        Iterator<String[]> lineIter = FormattedTextParser.parseTabDelimitedReader(reader);
        //organismReferenceId = getOrganism(taxonId);

        while (lineIter.hasNext()) {
            String[] line = lineIter.next();
            if (Pattern.matches("ID", line[0])) {
                // skipping header
                continue;
            }
            String geneId = line[0];
            String attr = line[1];

            Item item = createItem("Gene");
            item.setAttribute("primaryIdentifier", geneId);
            //item.setReference("organism", organismReferenceId);
            if (attr != null && !attr.trim().isEmpty()) {
                item.setAttribute(attributeName, attr);
            } else {
                LOG.warn("WARNING: attribute is empty for gene ID: " + geneId);  
            }
            item.addToCollection("dataSets", getDataSet());

            try {
                store(item);
            } catch(ObjectStoreException e) {
                throw new RuntimeException("Error while storing Gene item: " + item, e);
            }
        }
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
}
