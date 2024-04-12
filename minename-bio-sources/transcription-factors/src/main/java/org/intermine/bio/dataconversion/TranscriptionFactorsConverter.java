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
public class TranscriptionFactorsConverter extends BioFileConverter
{
    //
    private static final String DATASET_TITLE = "Grassius Transcription Factor data set";
    private static final String DATA_SOURCE_NAME = "Grassius";
    private static final int NUM_COLS = 5; // expected number of columns in input file
    private String taxonId = null;
    // Keep track of newly created Genes
    private HashMap<String,String> geneIdToRef = new HashMap<String, String>();

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public TranscriptionFactorsConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * Set the organism's taxon ID.
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
            throw new RuntimeException("taxonId needs to be set");
        }

        Iterator<String[]> lineIter = FormattedTextParser.parseTabDelimitedReader(reader);

        while (lineIter.hasNext()) {
            String[] line = lineIter.next();

            if (line.length < NUM_COLS) {
                throw new RuntimeException("Expected " + NUM_COLS + " columns, row has only " + line.length + " columns.");
            }

            if (Pattern.matches("TF_name", line[0])) {
                // skipping header
                continue;
            }

            String tfName = line[0];
            String family = line[1];
            String geneId = line[2];
            String geneName = line[3];
            String clone = line[4];

            String refId = getGene(geneId);

            Item tf = createItem("TranscriptionFactor");
            tf.setAttribute("tfName", tfName);  // primary identifier - cannot be empty
            if (fieldNotEmpty(family)) {
                tf.setAttribute("family", family);
            }
            if (fieldNotEmpty(geneName)) {
                tf.setAttribute("geneName", geneName);
            }
            if (fieldNotEmpty(clone)) {
                tf.setAttribute("clone", clone);
            }
            tf.setReference("gene", refId);
            store(tf);
        }
    }

    private String getGene(String geneId) throws ObjectStoreException {
        String refId = geneIdToRef.get(geneId);

        if (refId == null) {
            // Haven't created Gene yet, create it now and store ref
            Item gene = createItem("Gene");
            gene.setAttribute("primaryIdentifier", geneId);
            gene.setReference("organism", getOrganism(taxonId));
            refId = gene.getIdentifier();
            geneIdToRef.put(geneId, refId);
            store(gene);
        }
        return refId;
    }

    /**
     * Return true if field has a nonempty value
     */
    private boolean fieldNotEmpty(String fieldValue) {
        // Consider "-" to be empty / no value
        if ("-".equals(fieldValue)) {
            return false;
        }

        return StringUtils.isNotEmpty(fieldValue);
    }
}
