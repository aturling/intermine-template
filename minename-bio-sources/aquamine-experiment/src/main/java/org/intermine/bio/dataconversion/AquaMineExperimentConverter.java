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
 * Converter to load AquaMine Experiment data
 *
 * @author
 */
public class AquaMineExperimentConverter extends BioFileConverter
{
    //
    private static final String DATASET_TITLE = "SRA Experiment Metadata data set";
    private static final String DATA_SOURCE_NAME = "NCBI SRA";
    private static final int NUM_COLS = 17; // expected number of columns in tsv input file

    private ArrayList<String> headerNames = new ArrayList<String>();
    private HashMap<String,String> attributes = new HashMap<String, String>();
    private static final ArrayList<String> attributeNames = new ArrayList<String>();

    static {
        // Attribute names that can be directly stored from input file,
        // no further processing needed:
        attributeNames.add("bioProject");
        attributeNames.add("bioSample");
        attributeNames.add("libraryLayout");
        attributeNames.add("libraryName");
        attributeNames.add("librarySelection");
        attributeNames.add("librarySource");
        attributeNames.add("libraryStrategy");
        attributeNames.add("model");
        attributeNames.add("platform");
        attributeNames.add("run");
        attributeNames.add("sample");
        attributeNames.add("sampleName");
        attributeNames.add("sraStudy");
        attributeNames.add("tissue");
    }

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public AquaMineExperimentConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        // Assumes that the input file has unique experiment ids (one per line) so the
        // experiments may be created and stored immediately.
        Iterator<String[]> lineIter = FormattedTextParser.parseTabDelimitedReader(reader);

        while (lineIter.hasNext()) {
            String[] line = lineIter.next();

            if (line.length < NUM_COLS && StringUtils.isNotEmpty(line.toString())) {
                throw new RuntimeException("Invalid line, should be " + NUM_COLS + " columns but is " + line.length + " instead");
            }

            // Assume id is first column with "Experiment" as header name
            if (Pattern.matches("Experiment", line[0])) {
                // Parsing header
                parseHeader(line);
                continue;
            }

            // Create Experiment
            Item experiment = createItem("Experiment");
            String experimentId = line[0].trim();
            if (StringUtils.isEmpty(experimentId)) {
                break;
            }
            experiment.setAttribute("experimentId", experimentId); // primary identifier

            // Set attributes from row values
            setItemAttributes(line, experiment);

            // Special cases to be handled separately:

            // Set reference to organism
            setOrganismRef(experiment);
            // Also set organism name text field
            String orgNameKey = "organism"; // lowercase
            if (attributes.containsKey(orgNameKey)) {
                String orgName = attributes.get(orgNameKey);
                experiment.setAttribute("organismName", orgName);
            }

            try {
                store(experiment);
            } catch(Exception e) {
                throw new RuntimeException("Error while storing Experiment item: " + experiment);
            }
        }
    }

    /**
     * Parse file header
     *
     * @param line header line in input file
     */
    protected void parseHeader(String[] line) {
        for (int i = 0; i < line.length; i++) {
            // Store in lowercase for easier comparison
            String headerName = line[i].trim().toLowerCase();
            headerNames.add(headerName);
        }
    }

    /**
     * Set item attributes using attributeNames
     *
     * @param line line in input file
     * @param item item to set attributes
     */
    protected void setItemAttributes(String[] line, Item item) {
        // Store row values in (key, value) format with keys from header
        for (int i = 0; i < line.length; i++) {
            String value = getValue(line[i]);
            attributes.put(headerNames.get(i), value);
        }

        // Set attributes for item
        for (String attributeName : attributeNames) {
            if (attributes.containsKey(attributeName.toLowerCase())) {
                String attributeValue = attributes.get(attributeName.toLowerCase());
                if (StringUtils.isNotEmpty(attributeValue)) {
                    item.setAttribute(attributeName, attributeValue);
                }
            }
        }
    }

    /**
     * Set reference to organism in item
     *
     * @param item item with ontology term reference
     * @param taxonIdKey attributes key for taxon id (same as header column name)
     */
    protected void setOrganismRef(Item item, String taxonIdKey) {
        String key = taxonIdKey.toLowerCase(); // lowercase for matching with headers 
        if (attributes.containsKey(key)) {
            // Taxon id should not be empty
            String taxonId = attributes.get(key);
            item.setReference("organism", getOrganism(taxonId));
        }
    }

    /**
     * Set reference to organism in item using taxonId
     *
     * @param item item with ontology term reference
     */
    protected void setOrganismRef(Item item) {
        setOrganismRef(item, "taxonId");
    }

    /**
     * Get formatted field value
     *
     * @param unformattedStr unformatted field value
     * @return formatted field value
     */
    protected String getValue(String unformattedStr) {
        String value = unformattedStr.trim();
        // If column value is empty, set to empty string
        // Empty columns include: . - NA N/A
        if (".".equals(value) || "-".equals(value) || "NA".equals(value) | "N/A".equals(value)) {
            value = "";
        }
        return value;
    }
}
