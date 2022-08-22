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
 * Converter to load FAANG Experiment data
 *
 * @author
 */
public class FAANGExperimentConverter extends FAANGBioFileConverter
{
    //
    private static final String DATASET_TITLE = "SRA Experiment Metadata data set";
    private static final String DATA_SOURCE_NAME = "NCBI SRA";
    private static final int NUM_COLS = 22; // expected number of columns in tsv input file

    static {
        // Attribute names that can be directly stored from input file,
        // no further processing needed:
        attributeNames.add("btoId");
        attributeNames.add("btoName");
        attributeNames.add("chipAntibody");
        attributeNames.add("libraryName");
        attributeNames.add("libraryStrategy");
        attributeNames.add("librarySelection");
        attributeNames.add("librarySource");
        attributeNames.add("libraryLayout");
        attributeNames.add("platform");
        attributeNames.add("platformModel");
        attributeNames.add("project");
        attributeNames.add("run");
        attributeNames.add("specimenTag");
        attributeNames.add("tissue");
        attributeNames.add("uberonId");
        attributeNames.add("uberonName");
    }

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public FAANGExperimentConverter(ItemWriter writer, Model model) {
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

            // Set bioproject reference
            setBioProjectRef(experiment);
            // Also set bioproject text field
            String bioProjectKey = "bioproject"; // lowercase
            if (attributes.containsKey(bioProjectKey)) { 
                String projId = attributes.get(bioProjectKey);
                experiment.setAttribute("projectId", projId);
            }

            // Set biosample reference
            setBioSampleRef(experiment);

            // Handle ontology term ids
            setOntologyTermRef(experiment, "BRENDA Tissue Ontology", "brendaTissueOntology", "btoID", "BTOTerm");
            setOntologyTermRef(experiment, "Uber Anatomy Ontology", "uberAnatomyOntology", "UberonId", "UBERONTerm");

            try {
                store(experiment);
            } catch(Exception e) {
                System.out.println("Error while storing Experiment item: " + experiment + "\nStacktrace: " + e);
            }
        }
    }
}
