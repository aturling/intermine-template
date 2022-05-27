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
import org.intermine.model.bio.BioEntity;
import org.intermine.model.bio.DataSet;
import org.intermine.model.bio.Organism;
import org.intermine.objectstore.ObjectStoreException;

/**
 * Code for loading fasta for Chromosomes with assembly version.
 * @author
 */
public class FastaAssemblyLoaderTask extends BaseFastaAssemblyLoaderTask
{
    private static final Logger LOG = Logger.getLogger(FastaAssemblyLoaderTask.class);

    // FASTA format: primaryIdentifier|secondaryIdentifier|tertiaryIdentifier|name
    private String assemblyVersion = null;
    private String classAttributeAlias = "secondaryIdentifier";
    private String classAttributeAlias2 = "tertiaryIdentifier";
    private String classAttributeName = "name";

    /**
     * Set the assembly version for chromosomes
     * @param assemblyVersion
     */
    public void setAssemblyVersion(String assemblyVersion) {
        this.assemblyVersion = assemblyVersion;
    }

    /**
     * The attribute of the class created to set with the identifying field.  If not set will
     * be 'secondaryIdentifier'.
     * @param classAttributeAlias the class name
     */
    public void setClassAttributeAlias(String classAttributeAlias) {
        this.classAttributeAlias = classAttributeAlias;
    }

    /**
      * The attribute of the class created to set with the identifying field.  If not set will
      * be 'tertiaryIdentifier'.
      * @param classAttributeAlias2 the class name
      */
    public void setClassAttributeAlias2(String classAttributeAlias2) {
        this.classAttributeAlias2 = classAttributeAlias2;
    }

    /**
     * The attribute of the class created to set with the identifying field.  If not set will
     * be 'name'.
     * @param classAttributeName the class name
     */
    public void setClassAttributeName(String classAttributeName) {
        this.classAttributeName = classAttributeName;
    }

    /**
     * Do any extra processing needed for this record (extra attributes, objects, references etc.)
     * This method is called before the new objects are stored
     * @param bioJavaSequence the BioJava Sequence
     * @param flymineSequence the FlyMine Sequence
     * @param bioEntity the object that references the flymineSequence
     * @param organism the Organism object for the new InterMineObject
     * @param dataSet the DataSet object
     * @throws ObjectStoreException if a store() fails during processing
     */
    @Override
    protected void extraProcessing(Sequence bioJavaSequence, org.intermine.model.bio.Sequence
            flymineSequence, BioEntity bioEntity, Organism organism, DataSet dataSet)
        throws ObjectStoreException {

        // Additionally set attributeValueAlias, attributeValueAlias2, and 
        // attributeValueName, if present
        // These only make sense when bioEntity is Chromosome
        String[] headerAttributes = getSequenceHeaderAttributes(bioJavaSequence);
        if (headerAttributes.length > 1) {
            // If more than 1 component in header, there should be 4:
            if (headerAttributes.length < 4) {
                throw new RuntimeException("FASTA header does not have expected format.");
            }

            String attributeValueAlias = headerAttributes[1];
            String attributeValueAlias2 = headerAttributes[2];
            String attributeValueName = headerAttributes[3];

            try {
                bioEntity.setFieldValue(classAttributeAlias, attributeValueAlias);
            } catch (Exception e) {
                throw new IllegalArgumentException("Error setting: " + getClassName() + "."
                                                   + classAttributeAlias + " to: " + attributeValueAlias
                                                   + ". Does the attribute exist?");
            }
            try {
                bioEntity.setFieldValue(classAttributeAlias2, attributeValueAlias2);
            } catch (Exception e) {
                throw new IllegalArgumentException("Error setting: " + getClassName() + "."
                                                   + classAttributeAlias2 + " to: " + attributeValueAlias2
                                                   + ". Does the attribute exist?");
            }
            try {
                bioEntity.setFieldValue(classAttributeName, attributeValueName);
            } catch (Exception e) {
                throw new IllegalArgumentException("Error setting: " + getClassName() + "."
                        + classAttributeName + " to: " + attributeValueName
                        + ". Does the attribute exist?");
            }
        }

        // Set assembly version if present
        if (assemblyVersion != null) {
            try {
                bioEntity.setFieldValue("assembly", assemblyVersion);
            } catch (Exception e) {
                throw new IllegalArgumentException("Error setting: " + getClassName()
                        + ".assemblyVersion to: " + assemblyVersion);
            }
        }
    }

    /**
     * For the given BioJava Sequence object, return an identifier to be used when creating
     * the corresponding BioEntity.
     * @param bioJavaSequence the Sequenece
     * @return an identifier
     */
    @Override
    protected String getIdentifier(Sequence bioJavaSequence) {
        // Override getIdentifier() to return first attribute in sequence header
        // (+ suffix if set)
        String[] headerAttributes = getSequenceHeaderAttributes(bioJavaSequence);
        return headerAttributes[0] + getIdSuffix() ;
    }

    /**
     * Get sequence header attributes
     * @param bioJavaSequence the Sequenece
     * @return sequence header attributes (string array)
     */
    private String[] getSequenceHeaderAttributes(Sequence bioJavaSequence) {
        String sequenceHeader = getSequenceHeader(bioJavaSequence);
        return sequenceHeader.split("\\|");
    }
}
