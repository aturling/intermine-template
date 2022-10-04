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
import java.lang.String;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.apache.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;

/**
 * 
 * @author
 */
public class MaizeExpressionGeneConverter extends BioFileConverter
{
    protected static final Logger LOG = Logger.getLogger(MaizeExpressionGeneConverter.class);
    private static final String DATASET_TITLE = "Maize Gene Expression data set";
    private static final String DATA_SOURCE_NAME = "MaizeGDB";
    private String entityType;
    private String type;
    private String valueType = "";
    private String taxonId;
    private String orgRefId;

    private ArrayList<String> entities = new ArrayList<String>();
    private HashMap<String,Item> expressionItems = new HashMap<String, Item>();
    private HashMap<String,Item> transcriptItems = new HashMap<String, Item>();
    private HashMap<String,Item> entityItems = new HashMap<String, Item>();

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public MaizeExpressionGeneConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    public void setType(String type) {
        System.out.println("Setting type as " + type);
        this.type = type;
    }

    public void setEntityType(String entityType) {
        System.out.println("Setting entityType as " + entityType);
        this.entityType = entityType;
    }

    public void setTaxonId(String taxonId) {
        this.taxonId = taxonId;
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        orgRefId = getOrganism(taxonId);
        File currentFile = getCurrentFile();
        String currentFileName = currentFile.getName().toUpperCase();
        System.out.println("Processing file: " + currentFile.getName());

        Iterator<String[]> lineIter = FormattedTextParser.parseTabDelimitedReader(reader);
        while (lineIter.hasNext()) {
            String[] line = lineIter.next();
            if (Pattern.matches("Gene", line[0])) {
                // parsing header
                for (int i = 1; i < line.length; i++) {
                    entities.add(line[i]);
                }

                // create items for each entity where entityType can be Sample or Replicate
                // depending on configuration
                for (String entityName : entities) {
                    if (!entityItems.containsKey(entityName)) {
                        Item entityItem = createItem(entityType);
                        entityItem.setAttribute("name", entityName);
                        entityItem.setReference("organism", orgRefId);
                        entityItems.put(entityName, entityItem);
                    }
                }
                continue;
            }

            String transcriptId = line[0];
            for (int i = 1; i < line.length; i++) {
                String value = line[i];
                String entityName = entities.get(i - 1);
                String key = transcriptId + "-" + entityName;
                if (expressionItems.containsKey(key)) {
                    Item expressionItem = expressionItems.get(key);
                    expressionItem.setAttribute("FPKM", value);
                } else {
                    Item expressionItem = createItem("Expression");
                    expressionItem.setAttribute("type", type);
                    Item entityItem = entityItems.get(entityName);
                    expressionItem.setAttribute("entityName", entityName);
                    if (entityType.equals("Sample")) {
                        expressionItem.setReference("sample", entityItem.getIdentifier());
                        entityItem.addToCollection("meanExpression", expressionItem.getIdentifier());
                    } else if (entityType.equals("Replicate")) {
                        expressionItem.setReference("replicate", entityItem.getIdentifier());
                        entityItem.addToCollection("singleExpression", expressionItem.getIdentifier());
                    } else {
                        throw new RuntimeException("entityType is neither 'Sample' nor 'Replicate'");
                    }

                    expressionItem.setAttribute("FPKM", value);
                    expressionItems.put(key, expressionItem);
                }

                if (transcriptItems.containsKey(transcriptId)) {
                    Item expressionItem = expressionItems.get(key);
                    Item transcriptItem = transcriptItems.get(transcriptId);
                    expressionItem.setReference("gene", transcriptItem.getIdentifier());
                    transcriptItem.addToCollection("expression", expressionItem.getIdentifier());
                } else {
                    Item transcriptItem = createItem("Gene");
                    Item expressionItem = expressionItems.get(key);
                    transcriptItem.setAttribute("primaryIdentifier", transcriptId);
                    transcriptItem.setReference("organism", orgRefId);
                    transcriptItem.addToCollection("expression", expressionItem.getIdentifier());
                    expressionItem.setReference("gene", transcriptItem.getIdentifier());
                    transcriptItems.put(transcriptId, transcriptItem);
                }
            }
        }
    }

    /**
     * Storing all Expression Items
     */
    public void storeAllExpressionItems() {
        for (String key : expressionItems.keySet()) {
            try {
                store(expressionItems.get(key));
            } catch (Exception e) {
                System.out.println("Error while storing item:\n" + expressionItems.get(key) + "\nStackTrace:\n" + e);
            }
        }
    }

    /**
     * Storing all Transcript Items
     */
    public void storeAllTranscriptItems() {
        for (String key : transcriptItems.keySet()) {
            try {
                store(transcriptItems.get(key));
            } catch (Exception e) {
                System.out.println("Error while storing Transcript item:\n" + transcriptItems.get(key) + "\nStackTrace:\n" + e);
            }
        }
    }

    /**
     * Storing all Sample Items
     */
    public void storeAllEntityItems() {
        for (String key : entityItems.keySet()) {
            try {
                store(entityItems.get(key));
            } catch (Exception e) {
                System.out.println("Error while storing Sample item:\n" + entityItems.get(key) + "\nStackTrace:\n" + e);
            }
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception {
        storeAllExpressionItems();
        storeAllTranscriptItems();
        storeAllEntityItems();
    }
}
