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
 * A converter/retriever for the Maize GFF dataset.
 */

public class MaizeGFF3RecordHandler extends BaseGFF3RecordHandler
{

    /**
     * Create a new MaizeGFF3RecordHandler for the given data model.
     * @param model the model for which items will be created
     */
    public MaizeGFF3RecordHandler (Model model) {
        super(model);

        refsAndCollections.put("Transcript", "gene");
        refsAndCollections.put("CDS", "transcript");
        refsAndCollections.put("Exon", "transcripts");

        // Note: these may change with each release depending on the feature classes in the GFF files.
        // Comment out lines that don't apply to this mine release.
	//refsAndCollections.put("LincRNA", "gene");
	//refsAndCollections.put("MiRNA", "gene");
        refsAndCollections.put("MRNA", "gene");
	refsAndCollections.put("FivePrimeUTR", "transcripts");
	refsAndCollections.put("ThreePrimeUTR", "transcripts");
	//refsAndCollections.put("TRNA", "gene");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(GFF3Record record) {
        super.process(record);

        Item feature = getFeature();
        String clsName = feature.getClassName();
        // Set symbol and description
        setFeatureSymbol(record, "symbol");
        setFeatureDescription(record);

        if (clsName.equals("Gene")) {
            setFeatureStatus(record);
            setFeatureBiotype(record, "gene_biotype");
        } else {
            // Set transcript biotype, if applicable
            setFeatureBiotype(record, "transcript_biotype");
        }
    }
}
