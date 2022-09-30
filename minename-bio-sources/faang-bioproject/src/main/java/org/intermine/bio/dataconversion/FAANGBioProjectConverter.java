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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.Reader;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;

import org.apache.commons.lang.StringUtils;

/**
 * Converter to load FAANG BioProject data
 *
 * @author
 */
public class FAANGBioProjectConverter extends FAANGBioFileConverter
{
    private static final String DATASET_TITLE = "BioProject metadata data set";
    private static final String DATA_SOURCE_NAME = "NCBI and EBI";
    private static final int NUM_COLS = 9; // expected number of columns in tsv input file

    static {
        // Attribute names that can be directly stored from input file,
        // no further processing needed:
        attributeNames.add("assayType");
        attributeNames.add("bioProjectId");
        attributeNames.add("category");
        attributeNames.add("organismName");
        attributeNames.add("project");
        attributeNames.add("title");
    }

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public FAANGBioProjectConverter(ItemWriter writer, Model model) {
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
                throw new RuntimeException("Invalid line [" + line.toString() + "], should be " + NUM_COLS + " columns but is " + line.length + " instead");
            }

            // Assume id is first column with "BioprojectUniqueID" as header name
            if (Pattern.matches("BioprojectUniqueID", line[0])) {
                // Parsing header
                parseHeader(line);
                continue;
            }

            String bioProjectUniqueId = line[0].trim();
            if (StringUtils.isEmpty(bioProjectUniqueId)) {
                // Ignore empty line
                break;
            }

            // Create BioProject
            Item bioProject = getBioProject(bioProjectUniqueId);

            // Set attributes from row values
            setItemAttributes(line, bioProject);

            // Special cases to be handled separately:

            // Set reference to organism
            setOrganismRef(bioProject);

            // Set publication reference
            addToPublicationCollection(bioProject, "PMID");
        }
    }
}
