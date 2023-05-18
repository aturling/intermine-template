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

import java.util.Map;
import java.util.HashMap;

import org.intermine.bio.io.gff3.GFF3Record;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;

/**
 * A converter/retriever for the Ensembl GFF dataset via GFF files.
 */

public class EnsemblGFF3RecordHandler extends BaseGFF3RecordHandler
{
    /**
     * Create a new EnsemblGFF3RecordHandler for the given data model.
     * @param model the model for which items will be created
     */
    public EnsemblGFF3RecordHandler (Model model) {
        super(model);

        // relationship: exon <-> transcript <-> gene
        refsAndCollections.put("Transcript", "gene");
        refsAndCollections.put("CDS", "transcript");
        refsAndCollections.put("Exon", "transcripts");

        // relationship: transcribed_pseudogenic_exon <-> pseudogenic_transcript <-> transcribed_pseudogene
        refsAndCollections.put("PseudogenicTranscript", "gene");
        refsAndCollections.put("TranscribedPseudogenicExon", "transcripts");

        // Note: these may change with each release depending on the feature classes in the GFF files.
        // Comment out lines that don't apply to this mine release.
        refsAndCollections.put("CGeneSegment", "gene");
        refsAndCollections.put("DGeneSegment", "gene");
        refsAndCollections.put("JGeneSegment", "gene");
        refsAndCollections.put("LncRNA", "gene");
        refsAndCollections.put("MiRNA", "gene");
        refsAndCollections.put("MRNA", "gene");
        refsAndCollections.put("NcRNA", "gene");
        refsAndCollections.put("PreMiRNA", "gene");
        refsAndCollections.put("RNaseMRPRNA", "gene");
        refsAndCollections.put("RNasePRNA", "gene");
        refsAndCollections.put("RRNA", "gene");
        refsAndCollections.put("ScRNA", "gene");
        refsAndCollections.put("SnoRNA", "gene");
        refsAndCollections.put("SnRNA", "gene");
        refsAndCollections.put("SRPRNA", "gene");
        refsAndCollections.put("TRNA", "gene");
        refsAndCollections.put("VGeneSegment", "gene");
        refsAndCollections.put("YRNA", "gene");
        // TransposableElementGene and UnconfirmedTranscript parent cases handled above, inherit from Gene/Transcript
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

        // Remove secondary identifier (is it even set?)
        feature.removeAttribute("secondaryIdentifier");

        // Set symbol, and biotype (if applicable) for all features
        // (Gene won't have transcript biotype)
        setFeatureSymbol(record, "symbol_ensembl");
        setFeatureBiotype(record, "transcript_biotype");

        // Set protein identifier, if applicable
        setFeatureAttribute(record, "protein_id", "proteinIdentifier");

        // Extra processing according to class
        if (clsName.equals("Gene") || clsName.equals("TranscribedPseudogene") || clsName.equals("NontranscribedPseudogene")) {
            // Set description for gene only
            setFeatureDescription(record);

            // Set gene biotype
            setFeatureBiotype(record, "gene_biotype");

            // Set SO terms
            if (clsName.equals("TranscribedPseudogene") || clsName.equals("NontranscribedPseudogene")) {
                setFeatureSOTerm("pseudogene");
            }

        } else if (clsName.equals("TranscribedPseudogenicExon") || clsName.equals("NontranscribedPseudogenicExon")) {
            // Set SO terms
            setFeatureSOTerm("pseudogenic_exon");
        }
    }
}
