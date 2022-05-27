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

import java.io.Reader;
import java.util.Iterator;

import org.apache.commons.lang.StringUtils;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;


/**
 * A Converter that loads NCBI Gene ID to Ensembl Gene ID mapping to enable
 * proper integration of datasets using heterogenous identifiers.
 *
 * @author Deepak Unni
 * @author
 */
public class CustomGeneInfoConverter extends BioFileConverter
{
    private String dataSourceName, dataSetTitle, taxonId;
    private Item dataSource = null;
    private Item dataSet = null;

    private static final String FIELD_EMPTY_SYMBOL = ".";

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public CustomGeneInfoConverter(ItemWriter writer, Model model) {
        super(writer, model);
    }

    /**
     * Set taxon ID from project.xml
     * @param taxonId taxon ID
     */
    public void setTaxonId(String taxonId) {
        this.taxonId = taxonId;
    }

    /**
     * Data set title from project.xml
     * @param dataSetTitle the title of the DataSets
     */
    public void setDataSetTitle(String dataSetTitle) {
        this.dataSetTitle = dataSetTitle;
    }

    /**
     * Data source name from project.xml
     * @param dataSourceName name of datasource for items created
     */
    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        // Require taxon ID and gene source:
        if (StringUtils.isEmpty(taxonId)) {
            throw new RuntimeException("Taxon ID not set in project.xml");
        }

        Iterator<?> lineIter = FormattedTextParser.parseTabDelimitedReader(reader);
        while (lineIter.hasNext()) {
            String[] line = (String[]) lineIter.next();
            String primaryIdentifier = line[0].trim();
            String geneSource = line[1].trim();
            String symbol = line[2].trim();
            String description = line[3].trim();

            // Create gene
            Item geneItem = createItem("Gene");
            geneItem.setAttribute("primaryIdentifier", primaryIdentifier);
            geneItem.setAttribute("source", geneSource);
            if (StringUtils.isNotEmpty(symbol) && !symbol.equals(FIELD_EMPTY_SYMBOL)) {
                geneItem.setAttribute("symbol", symbol);
            }
            if (StringUtils.isNotEmpty(description) && !description.equals(FIELD_EMPTY_SYMBOL)) {
                geneItem.setAttribute("description", description);
            }
            geneItem.setReference("organism", getOrganism(taxonId));
            geneItem.addToCollection("dataSets", getDataSet());
            try {
                store(geneItem);
            } catch(Exception e) {
                throw new RuntimeException("Error while storing item: " + geneItem, e);
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
