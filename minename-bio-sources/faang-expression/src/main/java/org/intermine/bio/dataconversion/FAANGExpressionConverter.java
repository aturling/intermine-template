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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.apache.commons.lang.StringUtils;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;


/**
 * Converter to load FAANG expression data
 *
 * @author
 */
public class FAANGExpressionConverter extends BioFileConverter
{
    private static final String DATASET_TITLE = "Gene RNASeq expression data set";
    private static final String DATA_SOURCE_NAME = "NCBI SRA";

    private String orgRefId, valueType, geneSource, taxonId;
    private ArrayList<String> experimentIds = new ArrayList<String>();
    private HashMap<String, Item> expressionItems = new HashMap<String, Item>();
    private HashMap<String, String> experimentItemRefs = new HashMap<String, String>();
    private HashMap<String, String> geneItemRefs = new HashMap<String, String>();

    private static final String FIELD_EMPTY_SYMBOL = "-";

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public FAANGExpressionConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * Set geneSource
     * @param geneSource
     */
    public void setGeneSource(String geneSource) {
        System.out.println("Setting geneSource as " + geneSource);
        this.geneSource = geneSource;
    }

    /**
     * Set taxonId
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
        if (taxonId == null) {
            throw new RuntimeException("taxonId must be set in project.xml");
        }
        if (geneSource == null) {
            throw new RuntimeException("geneSource must be set in project.xml");
        }

        orgRefId = getOrganism(taxonId);

        Iterator<String[]> lineIter = FormattedTextParser.parseTabDelimitedReader(reader);
        System.out.println("Reading file ...");
        int lineCount = 0;
        while (lineIter.hasNext()) {
            String[] line = lineIter.next();
            lineCount += 1;

            if (lineCount % 1000 == 0) {
                System.out.println("\n\nProcessing line " + lineCount + "\n\n");
            }

            if (Pattern.matches("Gene", line[0])) {
                // parsing header
                // header format is: Gene experimentId1 experimentId2 experimentId3 ...
                for (int i = 1; i < line.length; i++) {
                    String experimentId = line[i];
                    experimentId = experimentId.replaceAll("\\s", ""); // remove any extra spaces
                    if (StringUtils.isEmpty(experimentId)) {
                        throw new RuntimeException("Experiment ID cannot be empty");
                    }
                    experimentIds.add(experimentId);
                }
                continue;
            }

            File currentFile = getCurrentFile();
            String currentFileName = currentFile.getName().toUpperCase();
            if (currentFileName.contains("FPKM")) {
                valueType = "FPKM";
            }
            else if (currentFileName.contains("TPM")) {
                valueType = "TPM";
            }
            else {
                throw new RuntimeException("Error: valueType never determined\nCheck file naming for " + currentFileName);
            }

            String geneId = line[0];
            if (StringUtils.isEmpty(geneId)) {
                throw new RuntimeException("Gene ID cannot be empty (line " + lineCount + ")");
            }

            for (int i = 1; i < line.length; i++) {
                String value = line[i];
                String experimentId = experimentIds.get(i - 1);

                // Create Expression for each column value (two files to be merged on gene + experiment id)
                // Only create expression if there's a value to store
                if (StringUtils.isNotEmpty(value) && !value.equals(FIELD_EMPTY_SYMBOL)) {
                    Item expressionItem = getExpression(experimentId, geneId);
                    // Set FPKM/TPM value
                    expressionItem.setAttribute(valueType, value);
                }
            }
        }
    }

    private Item getExpression(String experimentId, String geneId)
        throws ObjectStoreException {
        Item expressionItem = null;
        // Expressions unique up to gene and experiment ids:
        String key = geneId + "_" + experimentId;
        if (expressionItems.containsKey(key)) {
            expressionItem = expressionItems.get(key);
        } else {
            expressionItem = createItem("Expression");
            expressionItem.setAttribute("experiment", experimentId);
            expressionItem.setReference("organism", orgRefId);
            expressionItem.setReference("gene", getGene(geneId));
            expressionItem.setReference("sraMetadata", getExperiment(experimentId));
            expressionItems.put(key, expressionItem);
        }
        return expressionItem;
    }

    private String getGene(String primaryIdentifier)
        throws ObjectStoreException {
        String geneRef = null;
        if (geneItemRefs.containsKey(primaryIdentifier)) {
            geneRef = geneItemRefs.get(primaryIdentifier);
        } else {
            Item geneItem = createItem("Gene");
            geneItem.setAttribute("primaryIdentifier", primaryIdentifier);
            geneItem.setAttribute("source", geneSource);
            geneItem.setReference("organism", orgRefId);
            try {
                store(geneItem);
            } catch (ObjectStoreException e) {
                throw new RuntimeException("failed to store Gene with id: " + primaryIdentifier);
            }
            geneRef = geneItem.getIdentifier();
            geneItemRefs.put(primaryIdentifier, geneRef);
        }
        return geneRef;
    }

    private String getExperiment(String experimentId)
        throws ObjectStoreException {
        String experimentRef = null;
        if (experimentItemRefs.containsKey(experimentId)) {
            experimentRef = experimentItemRefs.get(experimentId);
        } else {
            Item experimentItem = createItem("Experiment");
            experimentItem.setAttribute("experimentId", experimentId);
            try {
                store(experimentItem);
            } catch (ObjectStoreException e) {
                throw new RuntimeException("failed to store experiment with id: " + experimentId);
            }
            experimentRef = experimentItem.getIdentifier();
            experimentItemRefs.put(experimentId, experimentRef);
        }
        return experimentRef;
    }

    /**
     * Store all processed items in the database
     */
    @Override
    public void close() throws Exception {
        for (String key : expressionItems.keySet()) {
            try {
                store(expressionItems.get(key));
            } catch (ObjectStoreException e) {
                throw new RuntimeException("failed to store Expression with key: " + key, e);
            }
        }
    }
}
