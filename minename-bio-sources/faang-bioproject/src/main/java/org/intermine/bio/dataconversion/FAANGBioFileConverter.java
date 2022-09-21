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

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.io.Reader;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.metadata.StringUtil;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;

import org.apache.commons.lang.StringUtils;

/**
 * Generic converter for FAANG metadata sets
 *
 * @author
 */
public abstract class FAANGBioFileConverter extends BioFileConverter
{
    private HashMap<String,Item> bioProjects = new HashMap<String, Item>();
    private HashMap<String,Item> bioSamples = new HashMap<String, Item>();
    private HashMap<String,Item> experiments = new HashMap<String, Item>();
    private HashMap<String,Item> ontologies = new HashMap<String, Item>();
    private HashMap<String,Item> ontologyTerms = new HashMap<String, Item>();
    private HashMap<String,Item> protocols = new HashMap<String, Item>();
    private HashMap<String,Item> publications = new HashMap<String, Item>();

    protected ArrayList<String> headerNames = new ArrayList<String>();
    protected HashMap<String,String> attributes = new HashMap<String, String>();
    protected static final ArrayList<String> attributeNames = new ArrayList<String>();

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public FAANGBioFileConverter(ItemWriter writer, Model model, String dataSourceName,
        String dataSetTitle) {
        super(writer, model, dataSourceName, dataSetTitle);
    }

    /**
     * Parse file header
     *
     * @param line header line in input file
     */
    protected void parseHeader(String[] line) {
        for (int i = 0; i < line.length; i++) {
            // Store in lowercase for easier comparison
            String headerName = line[i].trim().toLowerCase();
            headerNames.add(headerName);
        }
    }

    /**
     * Set item attributes using attributeNames
     *
     * @param line line in input file
     * @param item item to set attributes
     */
    protected void setItemAttributes(String[] line, Item item) {
        // Store row values in (key, value) format with keys from header
        for (int i = 0; i < line.length; i++) {
            String value = getValue(line[i]);
            attributes.put(headerNames.get(i), value);
        }

        // Set attributes for item
        for (String attributeName : attributeNames) {
            if (attributes.containsKey(attributeName.toLowerCase())) {
                String attributeValue = attributes.get(attributeName.toLowerCase());
                if (StringUtils.isNotEmpty(attributeValue)) {
                    item.setAttribute(attributeName, attributeValue);
                }
            }
        }
    }

    /**
     * Get an Item representation of a BioProject
     *
     * @param projId BioProject id
     * @return bioproject item
     */
    protected Item getBioProject(String projId) {
        Item bioProject = null;
        if (bioProjects.containsKey(projId)) {
            bioProject = bioProjects.get(projId);
        } else {
            bioProject = createItem("BioProject");
            bioProject.setAttribute("bioProjectUniqueId", projId);
            setOrganismRef(bioProject);
            bioProjects.put(projId, bioProject);
        }
        return bioProject;
    }

    /**
     * Get an Item representation of a BioSample
     *
     * @param sampleId BioSample id
     * @return biosample item
     */
    protected Item getBioSample(String sampleId) {
        Item bioSample = null;
        if (bioSamples.containsKey(sampleId)) {
            bioSample = bioSamples.get(sampleId);
        } else {
            bioSample = createItem("BioSample");
            bioSample.setAttribute("bioSampleId", sampleId);
            setOrganismRef(bioSample);
            bioSamples.put(sampleId, bioSample);
        }
        return bioSample;
    }

    /**
     * Get an Item representation of an experiment
     *
     * @param expId experiment id
     * @return experiment item
     */
    protected Item getExperiment(String expId) {
        Item experiment = null;
        if (experiments.containsKey(expId)) {
            experiment = experiments.get(expId);
        } else {
            experiment = createItem("Experiment");
            experiment.setAttribute("experimentId", expId);
            setOrganismRef(experiment);
            experiments.put(expId, experiment);
        }
        return experiment;
    }


    /**
     * Get an Item representation of a publication
     *
     * @param pubMedId PubMed id
     * @return publication item
     */
    protected Item getPublication(String pubMedId) throws ObjectStoreException {
        Item publication = null;
        if (publications.containsKey(pubMedId)) {
            publication = publications.get(pubMedId);
        } else {
            publication = createItem("Publication");
            publication.setAttribute("pubMedId", pubMedId);
            publications.put(pubMedId, publication);
        }
        return publication;
    }

    /**
     * Set reference to organism in item
     *
     * @param item item with ontology term reference
     * @param taxonIdKey attributes key for taxon id (same as header column name)
     */
    protected void setOrganismRef(Item item, String taxonIdKey) {
        String key = taxonIdKey.toLowerCase(); // lowercase for matching with headers 
        if (attributes.containsKey(key)) {
            // Taxon id should not be empty
            String taxonId = attributes.get(key);
            item.setReference("organism", getOrganism(taxonId));
        }
    }

    /**
     * Set reference to organism in item using taxonId
     *
     * @param item item with ontology term reference
     */
    protected void setOrganismRef(Item item) {
        setOrganismRef(item, "taxonId");
    }

    /**
     * Set reference to bioproject in item
     *
     * @param item item with bioproject reference
     * @param bioProjectIdKey attributes key for bioproject id (same as header column)
     */
    protected void setBioProjectRef(Item item, String bioProjectIdKey) {
        String key = bioProjectIdKey.toLowerCase(); // lowercase for matching with headers
        if (attributes.containsKey(key)) {
            String bioProjectId = attributes.get(key);
            if (StringUtils.isNotEmpty(bioProjectId)) {
                item.setReference("bioProject", getBioProject(bioProjectId));
            }
        }
    }

    /**     
     * Set reference to bioproject in item using bioProjectUniqueId
     *
     * @param item item with bioproject reference
     */
    protected void setBioProjectRef(Item item) {
        setBioProjectRef(item, "bioProjectUniqueId");
    }

    /**     
     * Set reference to biosample in item
     *
     * @param item item with biosample reference
     * @param bioSampleIdKey attributes key for biosample id (same as header column)
     */
    protected void setBioSampleRef(Item item, String bioSampleIdKey) {
        String key = bioSampleIdKey.toLowerCase(); // lowercase for matching with headers
        if (attributes.containsKey(key)) {
            String bioSampleId = attributes.get(key);
            if (StringUtils.isNotEmpty(bioSampleId)) {
                item.setReference("bioSample", getBioSample(bioSampleId));
            }
        }
    }

    /**     
     * Set reference to biosample in item using bioSample id
     *
     * @param item item with biosample reference
     */
    protected void setBioSampleRef(Item item) {
        setBioSampleRef(item, "bioSample");
    }

    /**
     * Add to experiment item collection
     *
     * @param item item with experiments
     * @param expKey attributes key for experiment id (same as header column name)
     */
    protected void addToExperimentCollection(Item item, String expKey)
        throws ObjectStoreException {
        String key = expKey.toLowerCase(); // lowercase for matching with headers 
        if (attributes.containsKey(key)) {
            String expIdsStr = attributes.get(key);
            if (!expIdsStr.isEmpty()) {
                // Multiple experiment ids comma separated
                String[] expIds = expIdsStr.split(",");
                for (int i = 0; i < expIds.length; i++) {
                    item.addToCollection("experiments", getExperiment(expIds[i]));
                }
            }
        }
    }

    /**
     * Add to publication item collection, if pub exists
     *
     * @param item item with publication
     * @param pubMedIdKey attributes key for pubmed id (same as header column name)
     */
    protected void addToPublicationCollection(Item item, String pubMedIdKey)
        throws ObjectStoreException {
        String key = pubMedIdKey.toLowerCase(); // lowercase for matching with headers 
        if (attributes.containsKey(key)) {
            String pubMedId = attributes.get(key);
            if (!pubMedId.isEmpty() && StringUtil.allDigits(pubMedId)) {
                item.addToCollection("publications", getPublication(pubMedId));
            }
        }
    }

    /**
     * Set reference to ontology term in item
     *
     * @param item item with ontology term reference
     * @param ontologyName name of ontology
     * @param ref reference field name
     * @param termKey attributes key (same as header column name)
     * @param type ontology term type
     */
    protected void setOntologyTermRef(Item item, String ontologyName, String ref, String termKey, String type)
        throws ObjectStoreException {
        String key = termKey.toLowerCase(); // lowercase for matching with headers
        if (attributes.containsKey(key)) {
            String termId = attributes.get(key);
            if (!termId.isEmpty()) {
                item.setReference(ref, getOntologyTerm(termId, type, ontologyName));
            }
        }
    }

    /**
     * Add ontology term to item collection
     *
     * @param item item with ontology term reference
     * @param ontologyName name of ontology
     * @param ref collection field name
     * @param termKey attributes key (same as header column name)
     * @param type ontology term type
     */
    protected void addToOntologyTermCollection(Item item, String ontologyName, String ref, String termKey, String type)
        throws ObjectStoreException {
        String key = termKey.toLowerCase(); // lowercase for matching with headers
        if (attributes.containsKey(key)) {
            String termId = attributes.get(key);
            if (!termId.isEmpty()) {
                item.addToCollection(ref, getOntologyTerm(termId, type, ontologyName));
            }
        }
    }

    /**
     * Set reference to protocol in item
     *
     * @param item item with protocol reference
     * @param ref reference field name
     * @param protocolKey attributes key (same as header column name)
     * @param name protocol name
     */
    protected void setProtocolRef(Item item, String ref, String protocolKey, String name) 
        throws ObjectStoreException {
        String key = protocolKey.toLowerCase(); // lowercase for matching with headers
        if (attributes.containsKey(key)) {
            String protocolUrl = attributes.get(key);
            if (StringUtils.isNotEmpty(protocolUrl)) {
                item.setReference(ref, getProtocol(protocolUrl, name));
            }
        }
    }

    /**
     * Get an Item representation of a subclass of OntologyTerm based on ontologyName
     *
     * @param id ontology term id
     * @param type ontology term type
     * @param ontologyName name of ontology
     * @return ontology term item
     */
    protected Item getOntologyTerm(String id, String type, String ontologyName)
        throws ObjectStoreException {
        Item ontologyTerm = null;
        if (ontologyTerms.containsKey(id)) {
            ontologyTerm = ontologyTerms.get(id);
        } else {
            ontologyTerm = createItem(type);
            ontologyTerm.setAttribute("identifier", id);
            Item ontology = getOntology(ontologyName);
            ontologyTerm.setReference("ontology", ontology);
            ontologyTerms.put(id, ontologyTerm);
        }
        return ontologyTerm;
    }

    /**
     * Get an Item representation of an Ontology
     * @param ontologyName
     * @return ontology item
     */
    protected Item getOntology(String ontologyName) throws ObjectStoreException {
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
     * Get an Item representation of a Protocol
     * @param url url
     * @param name name
     * @return protocol item
     */
    protected Item getProtocol(String url, String name) throws ObjectStoreException {
        Item protocol = null;
        // Protocols unique up to name and url (use both for key)
        String uniqueId = name + ":" + url;
        if (protocols.containsKey(uniqueId)) {
            protocol = protocols.get(uniqueId);
        } else {
            protocol = createItem("Protocol");
            protocol.setAttribute("name", name);
            protocol.setAttribute("url", url);
            protocols.put(uniqueId, protocol);
        }
        return protocol;
    }

    /**
     * Get formatted field value
     *
     * @param unformattedStr unformatted field value
     * @return formatted field value
     */
    protected String getValue(String unformattedStr) {
        String value = unformattedStr.trim();
        // If column value is empty, set to empty string
        // Empty columns include: . - NA N/A
        if (".".equals(value) || "-".equals(value) || "NA".equals(value) | "N/A".equals(value)) {
            value = "";
        }
        return value;
    }

    /**
     * Store all processed items in the database
     */
    @Override
    public void close() throws Exception {
        for (String key : bioProjects.keySet()) {
            store(bioProjects.get(key));
        }
        for (String key : bioSamples.keySet()) {
            store(bioSamples.get(key));
        }
        for (String key : experiments.keySet()) {
            store(experiments.get(key));
        }
        for (String key : ontologies.keySet()) {
            store(ontologies.get(key));
        }
        for (String key : ontologyTerms.keySet()) {
            store(ontologyTerms.get(key));
        }
        for (String key : protocols.keySet()) {
            store(protocols.get(key));
        }      
        for (String key : publications.keySet()) {
            store(publications.get(key));
        }
    }
}
