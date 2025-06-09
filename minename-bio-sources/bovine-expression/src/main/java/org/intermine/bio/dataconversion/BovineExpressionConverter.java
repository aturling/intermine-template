package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2025 FlyMine
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
 * Converter to load BovineMine Experiment data
 *
 * @author
 */
public class BovineExpressionConverter extends BioFileConverter
{
    //
    private static final String DATASET_TITLE = "BovineMine Expression metadata data set";
    private static final String DATA_SOURCE_NAME = "NCBI SRA";
    private static final String TAXON_ID = "9913";
    private static final int NUM_COLS = 31; // expected number of columns in tsv input file

    private ArrayList<String> headerNames = new ArrayList<String>();
    private HashMap<String,String> attributes = new HashMap<String, String>();
    private static final ArrayList<String> attributeNames = new ArrayList<String>();
    private HashMap<String,Item> ontologies = new HashMap<String, Item>();
    private HashMap<String,Item> ontologyTerms = new HashMap<String, Item>();

    static {
        // Attribute names that can be directly stored from input file,
        // no further processing needed:
        attributeNames.add("animalAgeAtCollection");
        attributeNames.add("averageReadLength");
        attributeNames.add("bioProject");
        attributeNames.add("bioSample");
        attributeNames.add("breed");
        attributeNames.add("btoName");
        attributeNames.add("cellType");
        attributeNames.add("description");
        attributeNames.add("developmentalStage");
        attributeNames.add("fastedStatus");
        attributeNames.add("gestationalAgeAtSampleCollection");
        attributeNames.add("healthStatusAtCollection");
        attributeNames.add("libraryLayout");
        attributeNames.add("libraryName");
        attributeNames.add("librarySelection");
        attributeNames.add("librarySource");
        attributeNames.add("libraryStrategy");
        attributeNames.add("model");
        attributeNames.add("name");
        attributeNames.add("organismName");
        attributeNames.add("organismPart");
        attributeNames.add("organismPartUberonName");
        attributeNames.add("platform");
        attributeNames.add("releaseDate");
        attributeNames.add("run");
        attributeNames.add("sampleName");
        attributeNames.add("sex");
        attributeNames.add("sraSampleAccession");
        attributeNames.add("sraStudyAccession");
        attributeNames.add("submissionDescription");
        attributeNames.add("submissionTitle");
    }

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public BovineExpressionConverter(ItemWriter writer, Model model) {
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

            // Create Expression Metadata object
            Item experiment = createItem("ExpressionMetadata");
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

            // Handle ontology term ids
            setOntologyTermRef(experiment, "Cell Ontology", "cellOntology", "CellTypeClID", "CLTerm");
            setOntologyTermRef(experiment, "Livestock Breed Ontology", "livestockBreedOntology", "BreedLboID", "LBOTerm"); 
            setOntologyTermRef(experiment, "BRENDA Tissue Ontology", "brendaTissueOntology", "btoID", "BTOTerm");
            setOntologyTermRef(experiment, "Uber Anatomy Ontology", "uberAnatomyOntology", "OrganismPartUberonID", "UBERONTerm");

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
     * @param item item with organism reference
     */
    protected void setOrganismRef(Item item) {
        item.setReference("organism", getOrganism(TAXON_ID));
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

    /**
     * Set reference to ontology term in item
     *
     * @param item item with ontology term reference
     * @param ontologyName name of ontology
     * @param ref reference field name
     * @param termKey attributes key (same as header column name)
     * @param type ontology term type
     */
    protected void setOntologyTermRef(Item item, String ontologyName, String ref, String termKey, String type)
        throws ObjectStoreException {
        String key = termKey.toLowerCase(); // lowercase for matching with headers
        if (attributes.containsKey(key)) {
            String termId = attributes.get(key);
            if (!termId.isEmpty()) {
                item.setReference(ref, getOntologyTerm(termId, type, ontologyName));
            }
        }
    }

    /**
     * Get an Item representation of a subclass of OntologyTerm based on ontologyName
     *
     * @param id ontology term id
     * @param type ontology term type
     * @param ontologyName name of ontology
     * @return ontology term item
     */
    protected Item getOntologyTerm(String id, String type, String ontologyName)
        throws ObjectStoreException {
        Item ontologyTerm = null;
        if (ontologyTerms.containsKey(id)) {
            ontologyTerm = ontologyTerms.get(id);
        } else {
            ontologyTerm = createItem(type);
            ontologyTerm.setAttribute("identifier", id);
            Item ontology = getOntology(ontologyName);
            ontologyTerm.setReference("ontology", ontology);
            ontologyTerms.put(id, ontologyTerm);
        }
        return ontologyTerm;
    }

    /**
     * Get an Item representation of an Ontology
     * @param ontologyName
     * @return ontology item
     */
    protected Item getOntology(String ontologyName) throws ObjectStoreException {
        Item ontology = null;
        if (ontologies.containsKey(ontologyName)) {
            ontology = ontologies.get(ontologyName);
        } else {
            ontology = createItem("Ontology");
            ontology.setAttribute("name", ontologyName);
            ontologies.put(ontologyName, ontology);
        }
        return ontology;
    }

    /**
     *
     */
    public void storeOntologyTermItems() {
        for (String key : ontologyTerms.keySet()) {
            try {
                // store Ontology term item
                store(ontologyTerms.get(key));
            } catch (Exception e) {
                System.out.println("Error while storing ontology term item:\n" + ontologyTerms.get(key) + "\nStackTrace:\n" + e);
            }
        }
    }

    /**
     *
     */
    public void storeOntologies() {
        for (String key : ontologies.keySet()) {
            try {
                // store Ontology item
                store(ontologies.get(key));
            } catch (Exception e) {
                System.out.println("Error while storing ontology item:\n" + ontologies.get(key) + "\nStackTrace:\n" + e);
            }
        }
    }

    public void close() {
        storeOntologyTermItems();
        storeOntologies();
   }
}
