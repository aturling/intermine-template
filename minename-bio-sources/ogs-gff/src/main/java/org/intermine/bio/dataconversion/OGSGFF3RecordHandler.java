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
 * A converter/retriever for the OGS dataset via GFF files.
 */

public class OGSGFF3RecordHandler extends BaseGFF3RecordHandler
{

    /**
     * Create a new OGSGFF3RecordHandler for the given data model.
     * @param model the model for which items will be created
     */
    public OGSGFF3RecordHandler (Model model) {
        super(model);

        refsAndCollections.put("Transcript", "gene");
        refsAndCollections.put("CDS", "transcript");
        refsAndCollections.put("Exon", "transcripts");

        refsAndCollections.put("FivePrimeUTR", "transcripts");
        refsAndCollections.put("MRNA", "gene");
        refsAndCollections.put("ThreePrimeUTR", "transcripts");

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(GFF3Record record) {
        super.process(record);

        Item feature = getFeature();
        String clsName = feature.getClassName();

        // Note default behavior is to set primary identifier to "ID=" value
        setFeatureDescription(record);

        // Extra processing according to class
        if (clsName.equals("Gene") || clsName.equals("MRNA") || clsName.equals("Polypeptide"))  {
            feature.removeAttribute("symbol");
        }
    }
}
