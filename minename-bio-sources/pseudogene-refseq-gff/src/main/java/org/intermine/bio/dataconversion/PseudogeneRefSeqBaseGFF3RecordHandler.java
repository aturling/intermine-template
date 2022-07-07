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

import org.intermine.bio.io.gff3.GFF3Record;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;

/**
 * A converter/retriever for the RefSeq pseudogene GFF dataset via GFF files.
 */

public class PseudogeneRefSeqBaseGFF3RecordHandler extends RefSeqGFF3RecordHandler
{

    /**
     * Create a new PseudogeneRefSeqBaseGFF3RecordHandler for the given data model.
     * @param model the model for which items will be created
     */
    public PseudogeneRefSeqBaseGFF3RecordHandler (Model model) {
        super(model);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(GFF3Record record) {
        // Do usual RefSeq-gff stuff (when applicable)
        super.process(record);

        Item feature = getFeature();
        String clsName = feature.getClassName();

        // Extra processing according to class
        if (clsName.equals("Pseudogene")) {
            // Update primary identifier according to "NCBI_Gene" key value
            handleDbxrefs(record, "primaryIdentifier", "NCBI_Gene");

            // Set gene biotype
            setFeatureBiotype(record, "gene_biotype");

        } else if (clsName.equals("PseudogenicTranscript")) {
            // Set protein identifier
            handleDbxrefs(record, "proteinIdentifier", "RefSeq_Prot");
        }
    }
}
