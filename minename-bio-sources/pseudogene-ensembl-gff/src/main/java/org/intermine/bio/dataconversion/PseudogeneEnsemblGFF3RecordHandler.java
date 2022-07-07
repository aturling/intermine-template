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
 * A converter/retriever for the Ensembl Pseudogene GFF dataset via GFF files.
 */

public class PseudogeneEnsemblGFF3RecordHandler extends EnsemblGFF3RecordHandler
{

    /**
     * Create a new PseudogeneEnsemblGFF3RecordHandler for the given data model.
     * @param model the model for which items will be created
     */
    public PseudogeneEnsemblGFF3RecordHandler (Model model) {
        super(model);

        // relationship: pseudogenic_exon <-> pseudogenic_transcript <-> pseudogene
        refsAndCollections.put("PseudogenicTranscript", "gene");
        refsAndCollections.put("PseudogenicExon", "transcripts");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(GFF3Record record) {
        // Do usual Ensembl-gff stuff (when applicable)
        super.process(record);

        Item feature = getFeature();
        String clsName = feature.getClassName();

        // Extra processing for pseudogenes
        if (clsName.equals("Pseudogene"))  {
            // Set description for pseudogene only
            setFeatureDescription(record);

            // Set gene biotype
            setFeatureBiotype(record, "gene_biotype");
        }
    }
}
