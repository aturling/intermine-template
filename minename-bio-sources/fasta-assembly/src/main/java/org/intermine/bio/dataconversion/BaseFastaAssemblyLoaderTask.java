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

import org.apache.log4j.Logger;
import org.biojava.nbio.core.sequence.template.Sequence;
import org.intermine.objectstore.ObjectStoreException;

/**
 * Code for loading fasta for Chromosomes with assembly version.
 * Incorporates changes to the base FastaLoaderTask that all other
 * custom FASTA loaders depend on.
 * @author
 */
public class BaseFastaAssemblyLoaderTask extends FastaLoaderTask
{
    private static final Logger LOG = Logger.getLogger(BaseFastaAssemblyLoaderTask.class);
    private String idSuffix = "";

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIdSuffix(String idSuffix) {
        this.idSuffix = idSuffix;
    }

    /**
     * Return identifier suffix value set with setIdSuffix().
     * @return the identifier suffix
     */
    public String getIdSuffix() {
        return idSuffix;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getIdentifier(Sequence bioJavaSequence) {
        String name = getSequenceHeader(bioJavaSequence);
        // description_line=sp|Q9V8R9-2|41_DROME
        if (name.contains("|")) {
            String[] bits = name.split("\\|");
            if (bits.length < 2) {
                return null;
            }
            name = bits[1];
        }
        // Append ID suffix if not already present
        if ( !(name.endsWith(idSuffix)) ) {
            name = name + idSuffix;
        }
        return name;
    }

    /**
     * Returns BioJava Sequence object header.
     * @return the header string
     */
    protected String getSequenceHeader(Sequence bioJavaSequence) {
        String name = bioJavaSequence.getAccession().getID();
        // getID does not seem to work properly
        // quick fix to get only the primaryidentifier
        if (name.contains(" ")) {
            String[] bits = name.split(" ");
            name = bits[0];
        }
        return name;
    }
}
