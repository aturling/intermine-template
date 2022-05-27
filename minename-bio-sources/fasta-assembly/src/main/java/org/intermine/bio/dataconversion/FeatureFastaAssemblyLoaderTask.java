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

import org.biojava.nbio.core.sequence.template.Sequence;
import org.intermine.metadata.Model;
import org.intermine.model.InterMineObject;
import org.intermine.model.bio.DataSet;
import org.intermine.model.bio.Gene;
import org.intermine.model.bio.MRNA;
import org.intermine.model.bio.Organism;
import org.intermine.objectstore.ObjectStoreException;

/**
 * Code for loading fasta for Chromosomes with assembly version, setting feature attribute 
 * from the FASTA header
 * @author
 */
public class FeatureFastaAssemblyLoaderTask extends BaseFastaAssemblyLoaderTask
{
    private String geneSource = null;
    private Map<String, Gene> geneMap = new HashMap<String, Gene>();
    private Map<String, MRNA> mrnaMap = new HashMap<String, MRNA>();

    /**
     * Gene source for any bioentities created
     * @param geneSource gene source
     */
    public void setGeneSource(String geneSource) {
        System.out.println("Setting gene source to " + geneSource);
        this.geneSource = geneSource;
    }

    /**
     * Return the gene source set with setGeneSource()
     * @return gene source
     */
    public String getGeneSource() {
        return this.geneSource;
    }

    /**
     * Return an MRNA for the given item.
     * @param mrnaIdentifier primaryIdentifier of MRNA to create
     * @param source gene source
     * @param organism orgnism of MRNA to create
     * @return the MRNA
     * @throws ObjectStoreException if problem fetching mrna
     */
    protected MRNA getMRNA(String mrnaIdentifier, String source, Organism organism, DataSet dataSet) 
        throws ObjectStoreException {
        if (mrnaMap.containsKey(mrnaIdentifier)) {
            return mrnaMap.get(mrnaIdentifier);
        }
        MRNA mrna = null;
        mrna = getDirectDataLoader().createObject(MRNA.class);
        mrna.setPrimaryIdentifier(mrnaIdentifier);
        mrna.setSource(source);
        mrna.setOrganism(organism);
        mrna.addDataSets(dataSet);
        getDirectDataLoader().store(mrna);
        mrnaMap.put(mrnaIdentifier, mrna);
        return mrna;
    }

    /**
     * Return a Gene object for the given item.
     * @param identifier id for gene
     * @param source gene source
     * @param organism the Organism to reference from the gene
     * @return the Gene
     * @throws ObjectStoreException if problem fetching gene
     */
    protected Gene getGene(String identifier, String source, Organism organism, DataSet dataSet)
        throws ObjectStoreException {
        if (geneMap.containsKey(identifier)) {
            return geneMap.get(identifier);
        }
        Gene gene = getDirectDataLoader().createObject(Gene.class);
        gene.setPrimaryIdentifier(identifier);
        gene.setSource(source);
        gene.setOrganism(organism);
        gene.addDataSets(dataSet);
        getDirectDataLoader().store(gene);
        geneMap.put(identifier, gene);
        return gene;
    }

    /**
     * For the given BioJava Sequence object, return an identifier to be used when creating
     * the corresponding BioEntity.
     * @param bioJavaSequence the Sequenece
     * @return an identifier
     */
    @Override
    protected String getIdentifier(Sequence bioJavaSequence) {
        // For this file format identifier is first ID in sequence header
        String name = getFeatureFastaHeaderAttribute(bioJavaSequence, 1);
        String suffix = getIdSuffix();
        // Append ID suffix if not already present
        if ( !(name.endsWith(suffix)) ) {
            name = name + suffix;
        }
        return name;
    }

    /**
     * Return string in the position of the sequence header or null if not found
     * @param bioJavaSequence the Sequenece
     * @param position position in header to return
     * @return the header attribution at the position or null if not found
     */
    protected String getFeatureFastaHeaderAttribute(Sequence bioJavaSequence, Integer position) {
        String attribute = null;
        String header = bioJavaSequence.getAccession().getID();
        Integer idx = position-1;
        if (header.contains(" ")) {
            String[] attributes = header.split(" ");
            if ( (idx >= 0) && (attributes.length > idx) ) {
                attribute = attributes[idx];
            }
        } else {
            // Something is wrong with the header, which means the fasta file needs to be checked
            // before proceeding with the load.
            throw new RuntimeException("Sequence header '" + header + "' is not in expected format.");
        }
        return attribute; 
    }

    /**
     * Return whether sequence is representative
     * @param isRepresentative string value of isRepresentative loaded from file ('T' or 'F')
     * @return boolean isRepresentative field value
     */
    protected boolean parseIsRepresentativeStr(String isRepresentative) {
        // Accept T/F, otherwise report formatting error
        if ("T".equalsIgnoreCase(isRepresentative)) {
            return true;
        } else if ("F".equalsIgnoreCase(isRepresentative)) {
            return false;
        } else {
            throw new RuntimeException("Sequence header field '" + isRepresentative + "' is not in expected format. Expected 'T' or 'F'.");
        }
    }

    // Functions no longer used:
    // getChromosome()
    // getLocationFromHeader()
    // isComplement()
    // getMin()
    // getMax()
}
