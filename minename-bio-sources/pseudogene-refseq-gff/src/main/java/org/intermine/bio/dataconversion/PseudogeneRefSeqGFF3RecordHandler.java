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

public class PseudogeneRefSeqGFF3RecordHandler extends PseudogeneRefSeqBaseGFF3RecordHandler
{

    /**
     * Create a new PseudogeneRefSeqGFF3RecordHandler for the given data model.
     * @param model the model for which items will be created
     */
    public PseudogeneRefSeqGFF3RecordHandler (Model model) {
        super(model);

        // relationship: pseudogenic_exon <-> pseudogenic_transcript <-> pseudogene
        refsAndCollections.put("PseudogenicTranscript", "gene");
        refsAndCollections.put("PseudogenicExon", "transcripts");
        refsAndCollections.put("PseudogenicRRNA", "gene");
        refsAndCollections.put("PseudogenicTRNA", "gene");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(GFF3Record record) {
        // Process pseudogenes, pseudogenic_transcripts, and pseudogenic_exons
        super.process(record);
    }
}
