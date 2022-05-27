package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2021 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.intermine.bio.io.gff3.GFF3Record;
import org.intermine.metadata.Model;
import org.intermine.metadata.StringUtil;
import org.intermine.xml.full.Item;

/**
 * A converter/retriever for RefSeq GFF files.
 */

public class RefSeqGFF3RecordHandler extends BaseGFF3RecordHandler
{
    /**
     * Create a new RefSeqGFF3RecordHandler for the given data model.
     * @param model the model for which items will be created
     */
    public RefSeqGFF3RecordHandler (Model model) {
        super(model);

        // Note: these may change with each release depending on the feature classes in the GFF files.
        // Class names not in model for this mine will be ignored.
        refsAndCollections.put("Transcript", "gene");
        refsAndCollections.put("CDS", "transcript");
        refsAndCollections.put("Exon", "transcripts");
        refsAndCollections.put("NcRNA", "gene");

        refsAndCollections.put("CGeneSegment", "gene");
        refsAndCollections.put("GuideRNA", "gene");
        refsAndCollections.put("LncRNA", "gene");
        refsAndCollections.put("MRNA", "gene");
        refsAndCollections.put("RRNA", "gene");
        refsAndCollections.put("SnoRNA", "gene");
        refsAndCollections.put("SnRNA", "gene");
        refsAndCollections.put("TRNA", "gene");
        refsAndCollections.put("VGeneSegment", "gene");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(GFF3Record record) {
        super.process(record);

        Item feature = getFeature();
        String clsName = feature.getClassName();

        // Set symbol, description, transcript biotype for all features (if present)
        // (CDS, Exon won't have symbol or description, Gene won't have transcript biotype)
        setFeatureSymbol(record, "symbol_ncbi");
        setFeatureDescription(record);
        setFeatureBiotype(record, "transcript_biotype");

        // Set note, exception if present
        setFeatureAttribute(record, "Note", "note");
        setFeatureAttribute(record, "exception", "exceptionNote");
  
        // Set status (ambiguous/frameshift cases only)
        // setFeatureStatus(record);
 
        // Update primary identifier to "RefSeq_NA" key value, if present (applies to transcript, ncRNA, mRNA, rRNA, etc.)
        handleDbxrefs(record, "primaryIdentifier", "RefSeq_NA");

        // Extra processing according to class
        // Not every mine will have all of these classes; class names not present in this mine will be ignored.
        if (clsName.equals("Gene"))  {
            // Update primary identifier according to "NCBI_Gene" key value
            handleDbxrefs(record, "primaryIdentifier", "NCBI_Gene");

            // Set gene biotype
            setFeatureBiotype(record, "gene_biotype");

            // Handle duplicate entities, if applicable
            if (converter.getLoadDuplicateEntities()) {
                createDuplicateEntities(record);
            }

        } else if (clsName.equals("Transcript") || clsName.equals("MRNA")) {
            // Set protein identifier
            handleDbxrefs(record, "proteinIdentifier", "RefSeq_Prot");

        } else if (clsName.equals("PrimaryTranscript") || clsName.equals("MiRNA")) {
            // Set miRBase identifier
            handleDbxrefs(record, "mirbaseIdentifier", "miRBase");
        }
    }
}
