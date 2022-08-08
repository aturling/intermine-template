package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2019 FlyMine
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
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.metadata.StringUtil;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;

import org.apache.commons.lang.StringUtils;

/**
 * 
 * @author
 */
public class FaangBiosampleConverter extends BioFileConverter
{
    //
    private static final String DATASET_TITLE = "BioSample metadata data set";
    private static final String DATA_SOURCE_NAME = "NCBI and EBI";
    private static final int NUM_COLS = 54; // expected number of columns in tsv input file
    private HashMap<String,Item> sampleItems = new HashMap<String, Item>();
    private ArrayList<String> headerNames = new ArrayList<String>();
    // Below are the attribute names that can be directly stored from input file
    // (no further processing needed)
    private ArrayList<String> attributeNames = new ArrayList<String>(
        Arrays.asList("animalAgeAtCollection",
                      "breed",
                      "breedLboId",
                      "breedOrphanetId",
                      "cellType",
                      "cellTypeClId",
                      "chipAntibody",
                      "description",
                      "descriptionUberonId",
                      "developmentalStage",
                      "developmentalStageEfoId",
                      "developmentalStageHsapdvId",
                      "developmentalStageUberonId",
                      "environmentalConditions",
                      "environmentalConditionsOwleolId",
                      "fastedStatus",
                      "gestationalAgeAtSampleCollection",
                      "healthStatusAtCollection",
                      "healthStatusAtCollectionHpId",
                      "healthStatusAtCollectionMondoId",
                      "healthStatusAtCollectionOrphanetId",
                      "healthStatusAtCollectionPatoId",
                      "markers",
                      "material",
                      "materialObiId",
                      "materialUberonId",
                      "name",
                      "numberOfPassages",
                      "organismName",
                      "organismPart",
                      "organismPartUberonId",
                      "organismPartUberonName",
                      "physiologicalCondition",
                      "physiologicalConditionOwlatolId",
                      "physiologicalConditions",
                      "project",
                      "purificationProtocol",
                      "releaseDate",
                      "sampleName",
                      "secondaryProject",
                      "sex",
                      "sexPatoId",
                      "specimenCollectionDate",
                      "specimenCollectionProtocol",
                      "specimenTag",
                      "strain",
                      "submissionDescription",
                      "submissionDescriptionUberonId",
                      "submissionTitle",
                      "submitterId",
                      "synonym",
                      "synonymUberonId",
                      "updateDate",
                      "tissue",
                      "btoId",
                      "btoName",
                      "bioSampleType"));

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public FaangBiosampleConverter(ItemWriter writer, Model model) {
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
                throw new RuntimeException("Invalid line, should be " + NUM_COLS + " columns but is " + line.length + " instead");
            }

            // Assume id is first column with "Biosample" as header name
            if (Pattern.matches("Biosample", line[0])) {
                // Parsing header
                for (int i = 0; i < line.length; i++) {
                    // Store in lowercase for easier comparison
                    String headerName = line[i].trim().toLowerCase();
                    headerNames.add(headerName);
                }
                continue;
            }

            String sampleId = line[0].trim();
            if (StringUtils.isEmpty(sampleId)) {
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

            addBioSample(sampleId, attributes);
        }
    }

    /**
     * Add/update a BioSample item and set its attributes
     *
     * @param sampleId sample id
     * @param attributes map of biosample attributes
     */
    private void addBioSample(String sampleId, HashMap<String,String> attributes) {
        // Check to see if we've encountered this sample id earlier in processing
        // If not, need to create item first
        if (!sampleItems.containsKey(sampleId)) {
            createBioSample(sampleId);
        }

        // Update rest of attributes (special cases to be handled separately below)
        for (String attributeName : attributeNames) {
            if (attributes.containsKey(attributeName.toLowerCase())) {
                String attributeValue = attributes.get(attributeName.toLowerCase());
                if (StringUtils.isNotEmpty(attributeValue)) {
                    sampleItems.get(sampleId).setAttribute(attributeName, attributeValue);
                }
            }
        }

        // Set reference to organism from taxon id
        String taxonIdKey = "taxonid"; // lowercase
        if (attributes.containsKey(taxonIdKey)) {
            String taxonId = attributes.get(taxonIdKey);
            sampleItems.get(sampleId).setReference("organism", getOrganism(taxonId));
        }

        // Handle component ids, if present
        String componentsKey = "biosamplecomponents"; // lowercase
        if (attributes.containsKey(componentsKey)) {
            String componentIds = attributes.get(componentsKey);
            if (StringUtils.isNotEmpty(componentIds)) {
                List<String> componentIdList = new ArrayList<String>( Arrays.asList(StringUtil.split(componentIds, ",")));
                for (String componentId : componentIdList) {
                    // Again, check to see if we've encountered this sample id earlier
                    // If not, need to create item first to add to collection
                    if (!sampleItems.containsKey(componentId)) {
                        createBioSample(componentId);
                    }

                    // Add this component to biosample component ids collection
                    sampleItems.get(sampleId).addToCollection("componentSampleIds", sampleItems.get(componentId).getIdentifier());
                }
            }
        }
    }

    /**
     * Create a new BioSample item with biosample id
     *
     * @param sampleId sample id
     */
    private void createBioSample(String sampleId) {
        Item sampleItem = createItem("BioSample");

        // Set sample id
        sampleItem.setAttribute("bioSampleId", sampleId);

        // Store in hasharray
        sampleItems.put(sampleId, sampleItem);
    }

    /**
     * Store all processed BioSamples in the database
     */
    @Override
    public void close() throws Exception {
        for (String key : sampleItems.keySet()) {
            store(sampleItems.get(key));
        }
    }
}
