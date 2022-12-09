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
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.HashMap;

import org.intermine.bio.io.gff3.GFF3Record;
import org.intermine.metadata.Model;
import org.intermine.metadata.StringUtil;
import org.intermine.xml.full.Item;
import org.apache.commons.lang.StringUtils;

/**
 * A converter/retriever for the QTL-GFF dataset via GFF files.
 */

public class QTLGFF3RecordHandler extends GFF3RecordHandler
{
    private HashMap<String, Item> publicationItems = new HashMap<String, Item>();
    private HashMap<String, Item> sequenceAlterationItems = new HashMap<String, Item>();
    private HashMap<String, Item> ontologyItems = new HashMap<String, Item>();
    private HashMap<String, Item> ontologyTermItems = new HashMap<String, Item>();
    // Map of attribute name -> field name for QTL fields to store (strings)
    private static final HashMap<String, String> attributesToSet = new HashMap<String, String>();
    // Map of attribute name -> field name for QTL fields to store (floats, handled separately)
    private static final HashMap<String, String> floatAttrsToSet = new HashMap<String, String>();
    // Map of ontology abbreviation (as appears in attribute list) -> Ontology name
    private static final HashMap<String, String> ontologiesToSet = new HashMap<String, String>(); 

    static {
        // Attribute name -> corresponding QTL field name
        attributesToSet.put("Abbrev", "abbreviation");
        attributesToSet.put("Bayes-value", "bayesValue");
        attributesToSet.put("breed", "breed");
        attributesToSet.put("Coord_src", "coordSource");
        attributesToSet.put("ID", "primaryIdentifier");
        attributesToSet.put("LOD-score", "lodScore");
        attributesToSet.put("LS-means", "lsMeans");
        attributesToSet.put("Map_Type", "mapType");
        attributesToSet.put("Model", "model");
        attributesToSet.put("Name", "name");
        attributesToSet.put("P-value", "pValue");
        attributesToSet.put("QTL_ID", "qtlId");
        attributesToSet.put("Test_Base", "testBase");
        attributesToSet.put("Tissue", "tissue");
	attributesToSet.put("Trait", "trait");
        attributesToSet.put("trait_ID", "traitId");

	// Float attributes
	floatAttrsToSet.put("Additive_Effect", "additiveEffect");
	floatAttrsToSet.put("Dominance_Effect", "dominanceEffect");
	floatAttrsToSet.put("F-Stat", "fStat");
	floatAttrsToSet.put("Likelihood_Ratio", "likelihoodRatio");
	floatAttrsToSet.put("Variance", "variance");

        // Ontology abbreviation -> Ontology name
        ontologiesToSet.put("CMO", "Clinical Measurement Ontology");
        ontologiesToSet.put("PTO", "Livestock Product Trait Ontology");
        ontologiesToSet.put("VTO", "Vertebrate Trait Ontology");
    }

    /**
     * Create a new QTLGFF3RecordHandler for the given data model.
     * @param model the model for which items will be created
     */
    public QTLGFF3RecordHandler (Model model) {
        super(model);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(GFF3Record record) {
        Item feature = getFeature();
        String clsName = feature.getClassName();
        if (clsName.equals("QTL")) {
            // Set QTL source
            feature.setAttribute("source", record.getSource());

            // Store attributes, if present
            for (Entry<String, String> e: attributesToSet.entrySet()) {
                String attrName = e.getKey();
                String fieldName = e.getValue();
                if (record.getAttributes().get(attrName) != null) {
                    String fieldValue = record.getAttributes().get(attrName).iterator().next();
		    if (fieldNotEmpty(fieldValue)) {
                        feature.setAttribute(fieldName, fieldValue);
		    }
                }
            }

            // Special case: float fields
	    for (Entry<String, String> e: floatAttrsToSet.entrySet()) {
                String attrName = e.getKey();
                String fieldName = e.getValue();
                if (record.getAttributes().get(attrName) != null) {
                    String fieldValue = record.getAttributes().get(attrName).iterator().next();
                    // Additional formatting may be required for floats:
                    fieldValue = formatFloatField(fieldValue);
                    if (fieldNotEmpty(fieldValue)) {
                        feature.setAttribute(fieldName, fieldValue);
                    }
                }
            }

	    // Special case: Capitalize significance
	    if (record.getAttributes().get("Significance") != null) {
	        String significance = record.getAttributes().get("Significance").iterator().next();
		if (fieldNotEmpty(significance)) {
		    feature.setAttribute("significance", StringUtils.capitalize(significance));
		}
            }

            // Special case: Publications
            if (record.getAttributes().get("PUBMED_ID") != null) {
                String pubMedId = record.getAttributes().get("PUBMED_ID").iterator().next();
		if (StringUtil.allDigits(pubMedId)) {
                    Item publication = getPublication(pubMedId);
                    feature.addToCollection("publications", publication.getIdentifier());
		}
            }

            // Special case: QTL type (replace underscores with spaces)
            if (record.getAttributes().get("qtl_type") != null) {
                String qtl_type = record.getAttributes().get("qtl_type").iterator().next().replace("_"," ");
                feature.setAttribute("type", qtl_type);
            }

            // Special case: Ontology terms
            for (Entry<String, String> e: ontologiesToSet.entrySet()) {
                String ontologyAbbr = e.getKey();
                String ontologyName = e.getValue();
                String attrName = ontologyAbbr + "_name";
                if (record.getAttributes().get(attrName) != null) {
                    String fieldName = StringUtils.uncapitalize(ontologyName.replaceAll("\\s+", ""));  // Ontology Name -> ontologyName
                    String termName = record.getAttributes().get(attrName).iterator().next();
                    Item ontologyTerm = getOntologyTerm(termName, ontologyName, ontologyAbbr);
                    feature.setReference(fieldName, ontologyTerm.getIdentifier());
                }
            }

            // Special case: Flank marker (if applicable)
            if (converter.getLoadSequenceAlterations()) {
                if (record.getAttributes().get("FlankMarker") != null) {
                    List<String> markers = record.getAttributes().get("FlankMarker");
                    feature.setAttribute("flankMarkers", StringUtils.join(markers, ", "));
                    ArrayList<String> sequenceAlterations = new ArrayList<String>();
                    for (String marker : markers) {
                        if (marker.startsWith("rs")) {
                            Item sequenceAlteration = getSequenceAlteration(marker);
                            sequenceAlterations.add(sequenceAlteration.getIdentifier());
                        }
                    }
                    for (int i = 0; i < sequenceAlterations.size(); i++) {
                        feature.addToCollection("snpsAsFlankMarkers", sequenceAlterations.get(i));
                    }
                }
            }
        }
    }

    /**
     * Get an Item representation of a subclass of OntologyTerm based on ontologyName
     * @param termName
     * @param ontologyName
     * @param ontologyAbbr
     * @return
     */
    private Item getOntologyTerm(String termName, String ontologyName, String ontologyAbbr) {
        Item ontologyTerm = null;
        String key = ontologyName + ":" + termName;
        if (ontologyTermItems.containsKey(key)) {
            ontologyTerm = ontologyTermItems.get(key);
        } else {
            if (ontologyAbbr.equals("CMO")) {
                ontologyTerm = converter.createItem("CMOTerm");
            } else if (ontologyAbbr.equals("PTO")) {
                ontologyTerm = converter.createItem("LPTTerm");
            } else if (ontologyAbbr.equals("VTO")) {
                ontologyTerm = converter.createItem("VTTerm");
            }
            ontologyTerm.setAttribute("name", termName);
            Item ontology = getOntology(ontologyName);
            ontologyTerm.setReference("ontology", ontology);
            addItem(ontologyTerm);
            ontologyTermItems.put(key, ontologyTerm);
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
        if (ontologyItems.containsKey(ontologyName)) {
            ontology = ontologyItems.get(ontologyName);
        } else {
            ontology = converter.createItem("Ontology");
            ontology.setAttribute("name", ontologyName);
            addItem(ontology);
            ontologyItems.put(ontologyName, ontology);
        }
        return ontology;
    }

    /**
     * Get an Item representation of a Publication
     * @param pubMedId
     * @return
     */
    private Item getPublication(String pubMedId) {
        Item publication = null;
        if (publicationItems.containsKey(pubMedId)) {
            publication = publicationItems.get(pubMedId);
        } else {
            publication = converter.createItem("Publication");
            publication.setAttribute("pubMedId", pubMedId);
            addItem(publication);
            publicationItems.put(pubMedId, publication);
        }
        return publication;
    }

    /**
     * Get an Item representation of a SequenceAlteration
     * @param identifier
     * @return
     */
    private Item getSequenceAlteration(String identifier) {
        Item sequenceAlteration = null;
        if (sequenceAlterationItems.containsKey(identifier)) {
            sequenceAlteration = sequenceAlterationItems.get(identifier);
        } else {
            sequenceAlteration = converter.createItem("SequenceAlteration");
            sequenceAlteration.setAttribute("primaryIdentifier", identifier);
            sequenceAlteration.setReference("organism", getOrganism());
            addItem(sequenceAlteration);
            sequenceAlterationItems.put(identifier, sequenceAlteration);
        }
        return sequenceAlteration;
    }

    /**
     * Return true if field has a nonempty value
     */
    private boolean fieldNotEmpty(String fieldValue) {
        // Consider "-" to be empty / no value
        if ("-".equals(fieldValue)) {
            return false;
        }

        return StringUtils.isNotEmpty(fieldValue);
    }

    private String formatFloatField(String fieldValue) {
        // Don't store if begins with '<' or '>'
        // (Only applies to a couple of values out of many, otherwise
        // value should be stored as string instead e.g. p-values)
        if (StringUtils.startsWith(fieldValue, "<") || StringUtils.startsWith(fieldValue, ">")) {
            return "";
        }
	String formattedValue = fieldValue;
	// Use correct hyphen character so negative numbers can be properly stored
	formattedValue = formattedValue.replace("−", "-");
	// After the above changes, remove all non-numeric characters (leave ., -, E)
        formattedValue = formattedValue.replaceAll("[^0-9eE.-]", "");
	return formattedValue;
    }
}
