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
import java.util.Map;

import org.intermine.metadata.Model;
import org.intermine.model.InterMineObject;
import org.intermine.model.bio.BioEntity;
import org.intermine.model.bio.DataSet;
import org.intermine.model.FastPathObject;
import org.intermine.model.bio.Gene;
import org.intermine.model.bio.MRNA;
import org.intermine.model.bio.Organism;
import org.biojava.nbio.core.sequence.template.Sequence;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.DynamicUtil;

/**
 * A fasta loader that understand the headers of RefSeq CDS fasta files and can make the
 * appropriate extra objects and references.
 * Adapted from AIPCDSFastaLoaderTask.java and FlyBaseCDSFastaLoaderTask.java
 * @author
 */
public class CDSFastaAssemblyLoaderTask extends FeatureFastaAssemblyLoaderTask
{
    /**
     * {@inheritDoc}
     */
    @Override
    protected void extraProcessing(Sequence bioJavaSequence, org.intermine.model.bio.Sequence flymineSequence, 
        BioEntity bioEntity, Organism organism, DataSet dataSet) throws ObjectStoreException {
        // Expected header format: MRNA.primaryIdentifier Gene.primaryIdentifier CodingSequence.proteinIdentifier CodingSequence.isRepresentative
        String mrnaIdentifier = getFeatureFastaHeaderAttribute(bioJavaSequence, 1);
        String geneIdentifier = getFeatureFastaHeaderAttribute(bioJavaSequence, 2);
        String proteinIdentifier = getFeatureFastaHeaderAttribute(bioJavaSequence, 3);
        String isRepresentative = getFeatureFastaHeaderAttribute(bioJavaSequence, 4);
        String geneSource = getGeneSource();

        if (proteinIdentifier != null) {
            bioEntity.setFieldValue("proteinIdentifier", proteinIdentifier);
        }
        if (isRepresentative != null) {
            bioEntity.setFieldValue("isRepresentative", parseIsRepresentativeStr(isRepresentative));
        }

        ObjectStore os = getIntegrationWriter().getObjectStore();
        Model model = os.getModel();
        if (model.hasClassDescriptor(model.getPackageName() + ".CodingSequence")) {
            Class<? extends FastPathObject> cdsCls =
            model.getClassDescriptorByName("CodingSequence").getType();
            if (!DynamicUtil.isInstance(bioEntity, cdsCls)) {
                throw new RuntimeException("the InterMineObject passed to "
                + "CDSFastaAssemblyLoaderTask.extraProcessing() is not a "
                + "CodingSequence: " + bioEntity);
            }
            bioEntity.setFieldValue("source", geneSource);
            if (mrnaIdentifier != null) {
                MRNA mrna = getMRNA(mrnaIdentifier, geneSource, organism, dataSet);
                if (mrna != null) {
                    bioEntity.setFieldValue("transcript", mrna);
                    mrna.setFieldValue("proteinIdentifier",proteinIdentifier);
                }
            }
            if (geneIdentifier != null) {
                Gene gene = getGene(geneIdentifier, geneSource, organism, dataSet);
                bioEntity.setFieldValue("gene", gene);
            }
            //Location loc = getLocationFromHeader(header, (SequenceFeature) bioEntity, organism);
            //getDirectDataLoader().store(loc);
        } else {
            throw new RuntimeException("Trying to load CodingSequence but CodingSequence does not exist in the data model");
        }
    }
}
