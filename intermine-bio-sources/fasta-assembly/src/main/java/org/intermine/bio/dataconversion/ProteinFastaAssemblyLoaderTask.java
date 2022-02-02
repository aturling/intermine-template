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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.biojava.nbio.core.sequence.template.Sequence;
import org.intermine.metadata.Model;
import org.intermine.model.FastPathObject;
import org.intermine.model.InterMineObject;
import org.intermine.model.bio.BioEntity;
import org.intermine.model.bio.DataSet;
import org.intermine.model.bio.Gene;
import org.intermine.model.bio.Organism;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.DynamicUtil;

/**
 * A fasta loader that understand the headers of Protein fasta files and can make the
 * appropriate extra objects and references.
 * @author
 * Adapted from AIPCDSFastaLoaderTask.java and FlyBaseCDSFastaLoaderTask.java
 */
public class ProteinFastaAssemblyLoaderTask extends FeatureFastaAssemblyLoaderTask
{
    /**
     * {@inheritDoc}
     */
    @Override
    protected void extraProcessing(Sequence bioJavaSequence, org.intermine.model.bio.Sequence flymineSequence, 
        BioEntity bioEntity, Organism organism, DataSet dataSet) throws ObjectStoreException {
        String geneIdentifier = getFeatureFastaHeaderAttribute(bioJavaSequence, 2);
        String mrnaIdentifier = getFeatureFastaHeaderAttribute(bioJavaSequence, 3);
        String geneSource = getGeneSource();

        ObjectStore os = getIntegrationWriter().getObjectStore();
        Model model = os.getModel();
        // Check to see if 'Polypeptide' class exists in the model
        if (model.hasClassDescriptor(model.getPackageName() + ".Polypeptide")) {
            Class<? extends FastPathObject> cdsCls = model.getClassDescriptorByName("Polypeptide").getType();
            if (!DynamicUtil.isInstance(bioEntity, cdsCls)) {
                throw new RuntimeException("the InterMineObject passed to "
                        + "ProteinFastaAssemblyLoaderTask.extraProcessing() is not a "
                        + "Polypeptide: " + bioEntity);
            }
            bioEntity.setFieldValue("source", geneSource);
            if (geneIdentifier != null) {
                Gene gene = getGene(geneIdentifier, geneSource, organism);
                // Setting the 'geneIdentifier' attribute for class 'Polypeptide'
                bioEntity.setFieldValue("geneIdentifier", geneIdentifier);
                // Setting the 'gene' reference for class 'Polypeptide'
                bioEntity.setFieldValue("gene", gene);
            }
            if (mrnaIdentifier != null) {
                InterMineObject mrna = getMRNA(mrnaIdentifier, getGeneSource(), organism, model);
                // Setting the 'mrnaIdentifier' attribute for class 'Polypeptide'
                bioEntity.setFieldValue("mrnaIdentifier", mrnaIdentifier);
                // Setting the 'mrna' reference for class 'Polypeptide'
                bioEntity.setFieldValue("mrna", mrna);
            }
        } else {
            throw new RuntimeException("Trying to load Polypeptide sequence but Polypeptide does not exist in the data model");
        }
    }
}
