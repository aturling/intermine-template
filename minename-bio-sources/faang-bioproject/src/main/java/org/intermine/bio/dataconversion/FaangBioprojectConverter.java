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
import java.io.Reader;

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
public class FaangBioprojectConverter extends BioFileConverter
{
    private Map<String, String> publications = new HashMap<String, String>();
    private static final String DATASET_TITLE = "BioProject metadata data set";
    private static final String DATA_SOURCE_NAME = "NCBI and EBI";
    private static final int NUM_COLS = 9; // expected number of columns in tsv input file

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public FaangBioprojectConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
       /*
        * Input file columns:
        * 0) BioProject unique id
        * 1) project id
        * 2) category
        * 3) assay type
        * 4) taxon id
        * 5) organism name
        * 6) pubmed id
        * 7) project
        * 8) title
        */
        Iterator<?> tsvIter;
        try {
            tsvIter = FormattedTextParser.parseTabDelimitedReader(reader);
        } catch (Exception e) {
            throw new RuntimeException("Cannot parse " + DATASET_TITLE + " file", e);
        }

        // skip header
        tsvIter.next();

        while (tsvIter.hasNext()) {
            String[] line = (String[]) tsvIter.next();

            if (line.length < NUM_COLS && StringUtils.isNotEmpty(line.toString())) {
                throw new RuntimeException("Invalid line [" + line.toString() + "], should be " + NUM_COLS + " columns but is " + line.length + " instead");
            }

            String bioProjectUniqueId = line[0].trim();
            if (StringUtils.isEmpty(bioProjectUniqueId)) {
                break;
            }

            String bioProjectId = getValue(line[1]);
            String category = getValue(line[2]);
            String assayType = getValue(line[3]);
            String taxonId = getValue(line[4]);
            String orgName = getValue(line[5]);
            String pubMedId = getValue(line[6]);
            String project = getValue(line[7]);
            String title = getValue(line[8]);

            // Create BioProject
            Item bioProjectItem = createItem("BioProject");
            // These fields should all be nonempty:
            bioProjectItem.setAttribute("bioProjectUniqueId", bioProjectUniqueId);
            bioProjectItem.setAttribute("bioProjectId", bioProjectId);
            bioProjectItem.setAttribute("category", category);
            bioProjectItem.setAttribute("assayType", assayType);
            bioProjectItem.setAttribute("organismName", orgName);
            bioProjectItem.setAttribute("project", project);
            bioProjectItem.setAttribute("title", title);

            // Set organism from taxon id
            bioProjectItem.setReference("organism", getOrganism(taxonId));

            // Set publication reference if it exists
            if (StringUtils.isNotEmpty(pubMedId)) {
                bioProjectItem.addToCollection("publications", getPublication(pubMedId));
            }

            store(bioProjectItem);
        }
    }

    private String getPublication(String pubMedId) throws ObjectStoreException {
        String refId = publications.get(pubMedId);
        if (refId == null) {
            Item pubItem = createItem("Publication");
            pubItem.setAttribute("pubMedId", pubMedId);
            store(pubItem);
            refId = pubItem.getIdentifier();
            publications.put(pubMedId, refId);
        }
        return refId;
    }

    private String getValue(String unformattedStr) {
        String value = unformattedStr.trim();
        if (".".equals(value) || "-".equals(value)) {
            value = "";
        }
        return value;
    }
}
