package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2019 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.Reader;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.metadata.StringUtil;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;

import org.apache.commons.lang.StringUtils;

/**
 * Converter to load FAANG BioSample data
 *
 * @author
 */
public class FAANGBioSampleConverter extends FAANGBioFileConverter
{
    private static final String DATASET_TITLE = "BioSample metadata data set";
    private static final String DATA_SOURCE_NAME = "NCBI and EBI";
    private static final int NUM_COLS = 54; // expected number of columns in tsv input file
    
    static {
        // Attribute names that can be directly stored from input file,
        // no further processing needed:
        attributeNames.add("animalAgeAtCollection");
        attributeNames.add("breed");
        attributeNames.add("breedLboId");
        attributeNames.add("breedOrphanetId");
        attributeNames.add("btoId");
        attributeNames.add("btoName");
        attributeNames.add("bioSampleType");
        attributeNames.add("cellType");
        attributeNames.add("cellTypeClId");
        attributeNames.add("chipAntibody");
        attributeNames.add("descriptionUberonId");
        attributeNames.add("developmentalStage");
        attributeNames.add("developmentalStageEfoId");
        attributeNames.add("developmentalStageHsapdvId");
        attributeNames.add("developmentalStageUberonId");
        attributeNames.add("environmentalConditions");
        attributeNames.add("environmentalConditionsOwleolId");
        attributeNames.add("fastedStatus");
        attributeNames.add("gestationalAgeAtSampleCollection");
        attributeNames.add("healthStatusAtCollection");
        attributeNames.add("healthStatusAtCollectionHpId");
        attributeNames.add("healthStatusAtCollectionMondoId");
        attributeNames.add("healthStatusAtCollectionOrphanetId");
        attributeNames.add("healthStatusAtCollectionPatoId");
        attributeNames.add("markers");
        attributeNames.add("material");
        attributeNames.add("materialObiId");
        attributeNames.add("materialUberonId");
        attributeNames.add("name");
        attributeNames.add("numberOfPassages");
        attributeNames.add("organismName");
        attributeNames.add("organismPart");
        attributeNames.add("organismPartUberonId");
        attributeNames.add("organismPartUberonName");
        attributeNames.add("physiologicalCondition");
        attributeNames.add("physiologicalConditionOwlatolId");
        attributeNames.add("physiologicalConditions");
        attributeNames.add("project");
        attributeNames.add("releaseDate");
        attributeNames.add("sampleName");
        attributeNames.add("secondaryProject");
        attributeNames.add("sex");
        attributeNames.add("sexPatoId");
        attributeNames.add("specimenCollectionDate");
        attributeNames.add("specimenTag");
        attributeNames.add("strain");
        attributeNames.add("submissionDescription");
        attributeNames.add("submissionDescriptionUberonId");
        attributeNames.add("submissionTitle");
        attributeNames.add("submitterId");
        attributeNames.add("synonym");
        attributeNames.add("synonymUberonId");
        attributeNames.add("tissue");
        attributeNames.add("updateDate");
    }

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public FAANGBioSampleConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        Iterator<?> tsvIter;
        try {
            tsvIter = FormattedTextParser.parseTabDelimitedReader(reader);
        } catch (Exception e) {
            throw new RuntimeException("Cannot parse " + DATASET_TITLE + " file", e);
        }

        while (tsvIter.hasNext()) {
            String[] line = (String[]) tsvIter.next();

            if (line.length < NUM_COLS && StringUtils.isNotEmpty(line.toString())) {
                throw new RuntimeException("Invalid line, should be " + NUM_COLS + " columns but is " + line.length + " instead");
            }

            // Assume id is first column with "Biosample" as header name
            if (Pattern.matches("Biosample", line[0])) {
                // Parsing header
                parseHeader(line);
                continue;
            }

            String sampleId = line[0].trim();
            if (StringUtils.isEmpty(sampleId)) {
                break;
            }

            // Create BioSample
            Item bioSample = getBioSample(sampleId);

            // Set attributes from row values
            setItemAttributes(line, bioSample);            

            // Special cases to be handled separately:

            // Capitalize description for consistency
	    if (attributes.containsKey("description")) {
                String description = attributes.get("description");
                if (StringUtils.isNotEmpty(description)) {
                    description = StringUtils.capitalize(description);
                    bioSample.setAttribute("description", description);
		}
            }

            // Set reference to organism
            setOrganismRef(bioSample);

            // Handle ontology term ids
            setOntologyTermRef(bioSample, "Animal Trait Ontology for Livestock", "animalTraitOntologyForLivestock", "PhysiologicalConditionOwlatolID", "ATOLTerm");
            setOntologyTermRef(bioSample, "BRENDA Tissue Ontology", "brendaTissueOntology", "btoID", "BTOTerm");
            setOntologyTermRef(bioSample, "Cell Ontology", "cellOntology", "CellTypeClID", "CLTerm");
            setOntologyTermRef(bioSample, "Environment Ontology for Livestock", "environmentOntologyForLivestock", "EnvironmentalConditionsOwleolID", "EOLTerm");
            setOntologyTermRef(bioSample, "Experimental Factor Ontology", "experimentalFactorOntology", "DevelopmentalStageEfoID", "EFOTerm");
            setOntologyTermRef(bioSample, "Human Developmental Stages Ontology", "humanDevelopmentalStagesOntology", "DevelopmentalStageHsapdvID", "HsapDvTerm");
            setOntologyTermRef(bioSample, "Human Phenotype Ontology", "humanPhenotypeOntology", "HealthStatusAtCollectionHpID", "HPTerm");
            setOntologyTermRef(bioSample, "Livestock Breed Ontology", "livestockBreedOntology", "BreedLboID", "LBOTerm");
            setOntologyTermRef(bioSample, "Mondo Disease Ontology", "mondoDiseaseOntology", "HealthStatusAtCollectionMondoID", "MONDOTerm");
            setOntologyTermRef(bioSample, "Ontology for Biomedical Investigations", "ontologyForBiomedicalInvestigations", "MaterialObiID", "OBITerm");
            setOntologyTermRef(bioSample, "Phenotype and Trait Ontology", "phenotypeAndTraitOntology", "HealthStatusAtCollectionPatoID", "PATOTerm");
            addToOntologyTermCollection(bioSample, "Orphanet Rare Disease Ontology", "orphanetRareDiseaseOntology", "BreedOrphanetID", "OrphanetTerm");
            addToOntologyTermCollection(bioSample, "Orphanet Rare Disease Ontology", "orphanetRareDiseaseOntology", "HealthStatusAtCollectionOrphanetID", "OrphanetTerm");
            addToOntologyTermCollection(bioSample, "Uber Anatomy Ontology", "uberAnatomyOntology", "DescriptionUberonID", "UBERONTerm");
            addToOntologyTermCollection(bioSample, "Uber Anatomy Ontology", "uberAnatomyOntology", "DevelopmentalStageUberonID", "UBERONTerm");
            addToOntologyTermCollection(bioSample, "Uber Anatomy Ontology", "uberAnatomyOntology", "OrganismPartUberonID", "UBERONTerm");

            // Set references to protocols
            setProtocolRef(bioSample, "purificationProtocol", "PurificationProtocol", "Purification Protocol");
	    setProtocolRef(bioSample, "specimenCollectionProtocol", "SpecimenCollectionProtocol", "Specimen Collection Protocol");

            // Handle component ids, if present
            String componentsKey = "biosamplecomponents"; // lowercase
            if (attributes.containsKey(componentsKey)) {
                String componentIds = attributes.get(componentsKey);
                if (StringUtils.isNotEmpty(componentIds)) {
                    List<String> componentIdList = new ArrayList<String>( Arrays.asList(StringUtil.split(componentIds, ",")));
                    for (String componentId : componentIdList) {
                        Item sampleComponent = getBioSample(componentId);

                        // Add this component to biosample component ids collection
                        bioSample.addToCollection("componentSampleIds", sampleComponent);
                    }
                }
            }
        }
    }
}
