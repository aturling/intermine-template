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
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;

import org.apache.commons.lang.StringUtils;

/**
 * 
 * @author
 */
public class FaangAnalysisConverter extends BioFileConverter
{
    //
    private static final String DATASET_TITLE = "Analysis metadata data set";
    private static final String DATA_SOURCE_NAME = "FAANG";

    private static final int NUM_COLS = 16; // expected number of columns in tsv input file
    private HashMap<String,Item> projectItems = new HashMap<String, Item>();
    private HashMap<String,Item> sampleItems = new HashMap<String, Item>();
    private ArrayList<String> headerNames = new ArrayList<String>();
    // Below are the attribute names that can be directly stored from input file
    // (no further processing needed)
    private ArrayList<String> attributeNames = new ArrayList<String>(
        Arrays.asList("source",
                      "project",
                      "analysisAccession",
                      "analysisType",
                      "annotationType",
                      "name",
                      "experimentAccession",
                      "experimentType",
                      "chipAntibody",
                      "specimenTag",
                      "tissue",
                      "software",
                      "peakWidth"));

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public FaangAnalysisConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        Iterator<?> tsvIter;
        try {
            tsvIter = FormattedTextParser.parseTabDelimitedReader(reader);
        } catch (Exception e) {
            throw new RuntimeException("Cannot parse " + DATASET_TITLE + " file", e);
        }

        while (tsvIter.hasNext()) {
            String[] line = (String[]) tsvIter.next();

            if (line.length < NUM_COLS && StringUtils.isNotEmpty(line.toString())) {
                throw new RuntimeException("Invalid line, should be " + NUM_COLS + "  columns but is " + line.length + " instead");
            }

            // Assume id is first column with "AnalysisTag" as header name
            if (Pattern.matches("AnalysisTag", line[0])) {
                // Parsing header
                for (int i = 0; i < line.length; i++) {
                    // Store in lowercase for easier comparison
                    String headerName = line[i].trim().toLowerCase();
                    headerNames.add(headerName);
                }
                continue;
            }

            String analysisId = line[0].trim();
            // In case of empty lines at end of file
            if (StringUtils.isEmpty(analysisId)) {
                break;
            }

            // Store row values in (key, value) format with keys from header
            HashMap<String,String> attributes = new HashMap<String, String>();
            for (int i = 0; i < line.length; i++) {
                String value = line[i].trim();
                // If column value is empty (designated by - or . in column), set to empty string
                if (".".equals(value) || "-".equals(value)) {
                    value = "";
                }
                attributes.put(headerNames.get(i), value);
            }

            createAnalysis(analysisId, attributes);
        }
    }

    /**
     * Create and store an Analysis item.
     *
     * @param analysisId analysis accession
     * @param ttributes map of analysis attributes
     * @throws ObjectStoreException os
     */
    private void createAnalysis(String analysisId, HashMap<String,String> attributes) throws ObjectStoreException {
        // Create Analysis item
        Item analysis = createItem("Analysis");

        // Set Analysis Accession
        analysis.setAttribute("analysisTag", analysisId);

        // Set rest of attributes (special cases to be handled separately below)
        for (String attributeName : attributeNames) {
            if (attributes.containsKey(attributeName.toLowerCase())) {
                String attributeValue = attributes.get(attributeName.toLowerCase());
                if (StringUtils.isNotEmpty(attributeValue)) {
                    analysis.setAttribute(attributeName, attributeValue);
                }
            }
        }

        // Set organism from taxon id
        String taxonIdKey = "taxonid"; // lowercase
        String taxonId = attributes.get(taxonIdKey); // taxon id required
        analysis.setReference("organism", getOrganism(taxonId));

        // Set bioproject reference and string field
        String bioProjectIdKey = "bioprojectuniqueid"; // lowercase
        if (attributes.containsKey(bioProjectIdKey)) {
            String bioProjectUniqueId = attributes.get(bioProjectIdKey);
            if (StringUtils.isNotEmpty(bioProjectUniqueId)) {
                setAnalysisReference(analysis, "BioProject", "bioProjectUniqueId", bioProjectUniqueId, projectItems, "bioProject", taxonId);
            }
        }
        if (attributes.containsKey("bioproject")) {
            String projectId = attributes.get("bioproject");
            if (StringUtils.isNotEmpty(projectId)) {
                analysis.setAttribute("projectId", projectId);
            }
        }

        // Set biosample reference
        String biosampleIdKey = "biosample"; // lowercase
        if (attributes.containsKey(biosampleIdKey)) {
            String sampleId = attributes.get(biosampleIdKey);
            if (StringUtils.isNotEmpty(sampleId)) {
                setAnalysisReference(analysis, "BioSample", "bioSampleId", sampleId, sampleItems, "bioSample", taxonId);
            }
        }

        // Store in database
        store(analysis);
    }

    /**
     * Set reference to item in analysis table
     *
     * @param analysis analysis Item
     * @param itemType type of item being referenced
     * @param idName id field name for reference item
     * @param id id value for reference item
     * @param items hashmap of reference items keyed by their ids
     * @param refName name of reference field
     * @param taxon id taxon id
     */
    private void setAnalysisReference(Item analysis, String itemType, String idName, String id,
        HashMap<String,Item> items, String refName, String taxonId) throws ObjectStoreException {
        // Set reference, creating new Item if necessary
        if (!items.containsKey(id)) {
            Item newItem = createItem(itemType);
            newItem.setAttribute(idName, id);
            newItem.setReference("organism", getOrganism(taxonId));
            store(newItem);
            items.put(id, newItem);
        }
        analysis.setReference(refName, items.get(id).getIdentifier());
    }
}
