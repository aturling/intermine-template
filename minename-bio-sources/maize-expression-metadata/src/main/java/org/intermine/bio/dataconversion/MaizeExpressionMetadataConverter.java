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

import java.io.Reader;
import java.lang.String;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;

/**
 * 
 * @author
 */
public class MaizeExpressionMetadataConverter extends BioFileConverter
{
    protected static final Logger LOG = Logger.getLogger(MaizeExpressionMetadataConverter.class);

    private static final String DATASET_TITLE = "Maize Expression Metadata data set";
    private static final String DATA_SOURCE_NAME = "MaizeGDB";
    private static final String PLANT_ONTOLOGY = "Plant Ontology";

    private HashMap<String,Item> sampleMap = new HashMap<String, Item>();
    private HashMap<String,Item> ontologyTermMap = new HashMap<String, Item>();
    private HashMap<String,Item> ontologies = new HashMap<String, Item>();
    private String taxonId;
    private String orgRefId;

    /**
     * Set taxon ID
     */
    public void setTaxonId(String taxonId) {
        this.taxonId = taxonId;
    }

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public MaizeExpressionMetadataConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        orgRefId = getOrganism(taxonId);
        Iterator<String[]> lineIter = FormattedTextParser.parseTabDelimitedReader(reader);
        while(lineIter.hasNext()) {
            String[] line = lineIter.next();
            String sampleReplicateName = line[0];
            String sampleName = line[1];
            String sraBioSampleId = line[2];
            String sraRunId = line[3];
            String[] sraRunIdArray = sraRunId.split(";");
            sraRunId = String.join(", ", sraRunIdArray);
            String sraExperimentId = line[4];
            String[] sraExperimentIdArray = sraExperimentId.split(";");
            sraExperimentId = String.join(", ", sraExperimentIdArray);
            String sraStudyId = line[5];
            String sraBioProjectId = line[6];
            String libraryLayout = line[7];
            String sraSampleId = line[8];
            String bioSampleDescription = line[9];
            String organGroup = line[10];
            String tissueDescription = line[11];
            String poTerm = line[12].replaceAll(" ","");
            String poTermName = line[13];
            String growthStage = line[14];
            String replicateNumber = line[15];
            String qTellerId = line[16];
            String namLine = line[17];
            String libraryName = line[18];

            if (!fieldNotEmpty(sampleName)) {
                throw new RuntimeException("sampleName cannot be empty");
            }
            if (!fieldNotEmpty(sampleReplicateName)) {
                throw new RuntimeException("sampleReplicateName cannot be empty");
            }

            // create or get Sample item
            Item sampleItem;
            if (sampleMap.containsKey(sampleName)) {
                sampleItem = sampleMap.get(sampleName);
            } else {
                sampleItem = createItem("Sample");
                sampleItem.setAttribute("name", sampleName);
                sampleItem.setAttribute("sampleName", sampleName);
                sampleItem.setReference("organism", orgRefId);
                if (fieldNotEmpty(sraStudyId)) sampleItem.setAttribute("sraStudyId", sraStudyId);
                if (fieldNotEmpty(sraBioProjectId)) sampleItem.setAttribute("sraBioProjectId", sraBioProjectId);
                if (fieldNotEmpty(libraryLayout)) sampleItem.setAttribute("libraryLayout", libraryLayout);
                if (fieldNotEmpty(growthStage)) sampleItem.setAttribute("growthStage", growthStage);
                sampleItem.setReference("poName", getPlantOntologyTerm(poTerm, poTermName));
                if (fieldNotEmpty(organGroup)) sampleItem.setAttribute("organGroup", organGroup);
                if (fieldNotEmpty(tissueDescription)) sampleItem.setAttribute("tissueDescription", tissueDescription);
                if (fieldNotEmpty(qTellerId)) sampleItem.setAttribute("qTellerId", qTellerId);
                if (fieldNotEmpty(namLine)) sampleItem.setAttribute("namLine", namLine);
                sampleMap.put(sampleName, sampleItem);
            }

            // create Replicate item
            Item repItem = createItem("Replicate");
            repItem.setAttribute("name", sampleReplicateName);
            repItem.setAttribute("sampleReplicateName", sampleReplicateName);
            repItem.setReference("organism", orgRefId);
            if (fieldNotEmpty(sraBioSampleId)) repItem.setAttribute("sraBioSampleId", sraBioSampleId);
            if (fieldNotEmpty(sraRunId)) repItem.setAttribute("sraRunId", sraRunId);
            if (fieldNotEmpty(sraExperimentId)) repItem.setAttribute("sraExperimentId", sraExperimentId);
            if (fieldNotEmpty(sraSampleId)) repItem.setAttribute("sraSampleId", sraSampleId);
            if (fieldNotEmpty(bioSampleDescription)) repItem.setAttribute("bioSampleDescription", bioSampleDescription);
            if (fieldNotEmpty(replicateNumber)) repItem.setAttribute("replicateNumber", replicateNumber);
            if (fieldNotEmpty(libraryName)) repItem.setAttribute("libraryName", libraryName);

            // set Sample as sample reference for Replicate
            repItem.setReference("sample", sampleItem.getIdentifier());

            try {
                // store Replicate item
                store(repItem);
            } catch (Exception e) {
                System.out.println("Error while storing item: " + repItem + "\n" + e);
            }
        }
    }

    /**
     * Get an Item representation of Plant Ontology term
     * @param termName
     * @param ontologyName
     * @return
     */
    private Item getPlantOntologyTerm(String identifier, String name) {
        Item ontologyTerm = null;
        if (ontologyTermMap.containsKey(identifier)) {
            ontologyTerm = ontologyTermMap.get(identifier);
        } else {
            ontologyTerm = createItem("POTerm");
            ontologyTerm.setAttribute("identifier", identifier);
            ontologyTerm.setAttribute("name", name);
            ontologyTerm.setReference("ontology", getOntology(PLANT_ONTOLOGY));
            ontologyTermMap.put(identifier, ontologyTerm);
        }
        return ontologyTerm;
    }

    /**
     * Get an Item representation of an Ontology
     * @param ontologyName
     * @return
     */
    private Item getOntology(String ontologyName) {
        Item ontology = null;
        if (ontologies.containsKey(ontologyName)) {
            ontology = ontologies.get(ontologyName);
        } else {
            ontology = createItem("Ontology");
            ontology.setAttribute("name", ontologyName);
            ontologies.put(ontologyName, ontology);
        }
        return ontology;
    }

    /**
     * Return true if field has a nonempty value
     */
    protected boolean fieldNotEmpty(String fieldValue) {
        // Consider "-", ".", "NA", or "N/A" to be empty / no value
        if ("-".equals(fieldValue) || ".".equals(fieldValue) || "NA".equals(fieldValue) || "N/A".equals(fieldValue)) {
            return false;
        }

        return StringUtils.isNotEmpty(fieldValue);
    }

    /**
     *
     */
    public void storeItems() {
        for (String key : sampleMap.keySet()) {
            try {
                // store Sample item
                store(sampleMap.get(key));
            } catch (Exception e) {
                System.out.println("Error while storing sample item:\n" + sampleMap.get(key) + "\nStackTrace:\n" + e);
            }
        }
    }

    /**
     *
     */
    public void storeOntologyTermItems() {
        for (String key : ontologyTermMap.keySet()) {
            try {
                // store Ontology term item
                store(ontologyTermMap.get(key));
            } catch (Exception e) {
                System.out.println("Error while storing ontology term item:\n" + ontologyTermMap.get(key) + "\nStackTrace:\n" + e);
            }
        }
    }

    /**
     *
     */
    public void storeOntologies() {
        for (String key : ontologies.keySet()) {
            try {
                // store Ontology item
                store(ontologies.get(key));
            } catch (Exception e) {
                System.out.println("Error while storing ontology item:\n" + ontologies.get(key) + "\nStackTrace:\n" + e);
            }
        }
    }

    /**
     *
     */
    public void close() {
        storeItems();
        storeOntologyTermItems();
        storeOntologies();
    }
}
