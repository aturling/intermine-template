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
 * Converter to load FAANG Analysis data
 *
 * @author
 */
public class FAANGAnalysisConverter extends FAANGBioFileConverter
{
    //
    private static final String DATASET_TITLE = "Analysis metadata data set";
    private static final String DATA_SOURCE_NAME = "FAANG";

    private static final int NUM_COLS = 16; // expected number of columns in tsv input file

    static {
        // Attribute names that can be directly stored from input file,
        // no further processing needed:
        attributeNames.add("analysisAccession");
        attributeNames.add("analysisTag");
        attributeNames.add("analysisType");
        attributeNames.add("annotationType");
        attributeNames.add("chipAntibody");
        attributeNames.add("experimentAccession");
        attributeNames.add("experimentType");
        attributeNames.add("name");
        attributeNames.add("peakWidth");
        attributeNames.add("project");
        attributeNames.add("software");
        attributeNames.add("source");
        attributeNames.add("specimenTag");
        attributeNames.add("tissue");
    }

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public FAANGAnalysisConverter(ItemWriter writer, Model model) {
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
                parseHeader(line);
                continue;
            }

            String analysisId = line[0].trim();
            // In case of empty lines at end of file
            if (StringUtils.isEmpty(analysisId)) {
                break;
            }

            // Create Analysis item
            Item analysis = createItem("Analysis");

            // Set attributes from row values
            setItemAttributes(line, analysis); 

            // Special cases to be handled separately:

            // Set reference to organism
            setOrganismRef(analysis);

            // Set bioproject reference
            setBioProjectRef(analysis);
            // Also set bioproject id text field
            if (attributes.containsKey("bioproject")) {
                String projectId = attributes.get("bioproject");
                if (StringUtils.isNotEmpty(projectId)) {
                    analysis.setAttribute("projectId", projectId);
                }
            }

            // Set biosample reference
            setBioSampleRef(analysis);

            // Add to experiments collection
            addToExperimentCollection(analysis, "ExperimentAccession");

            // Store in database
            store(analysis);
        }
    }
}
