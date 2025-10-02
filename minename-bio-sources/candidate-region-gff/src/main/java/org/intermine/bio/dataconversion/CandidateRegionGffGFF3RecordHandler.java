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

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.HashMap;

import org.intermine.bio.io.gff3.GFF3Record;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;
import org.apache.commons.lang.StringUtils;

/**
 * A converter/retriever for the CandidateRegionGff dataset via GFF files.
 */

public class CandidateRegionGffGFF3RecordHandler extends BaseGFF3RecordHandler
{
    private static final HashMap<String, String> attributesToSet = new HashMap<String, String>();
    private HashMap<String, Item> geneItems = new HashMap<String, Item>();

    static {
        // Multi-value fields:
        // Attribute name -> corresponding feature field name
        attributesToSet.put("breed", "breed");
        attributesToSet.put("breed_class", "breedClass");
        attributesToSet.put("breed_origin", "breedOrigin");
        attributesToSet.put("population", "population");
        attributesToSet.put("stat_test", "statTest");
        attributesToSet.put("target_breeds", "targetBreeds");
    }

    /**
     * Create a new CandidateRegionGffGFF3RecordHandler for the given data model.
     * @param model the model for which items will be created
     */
    public CandidateRegionGffGFF3RecordHandler (Model model) {
        super(model);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(GFF3Record record) {
        super.process(record);

        Item feature = getFeature();
        String clsName = feature.getClassName();
        String pubUrl = null;

        // Multi-value fields - store as comma separated strings
        for (Entry<String, String> e: attributesToSet.entrySet()) {
            String attrName = e.getKey();
            String fieldName = e.getValue();
            if (record.getAttributes().get(attrName) != null) {
                String fieldValue = String.join(", ", record.getAttributes().get(attrName));
                if (fieldNotEmpty(fieldValue)) {
                    feature.setAttribute(fieldName, fieldValue);
                }
            }
        }

        // Statistics value (float)
        if (record.getAttributes().get("stat_value") != null) {
            String fieldValue = record.getAttributes().get("stat_value").iterator().next();
            // Additional formatting may be required for floats:
            fieldValue = formatFloatField(fieldValue);
            if (fieldNotEmpty(fieldValue)) {
                feature.setAttribute("statValue", fieldValue);
            }
        }

        // Publication url
        // Some pubs don't have PubMed ID, only DOI so won't have publication item
        // Add url string for convenience
        if (record.getAttributes().get("PUBMED_ID") != null) {
            String pubMedId = record.getAttributes().get("PUBMED_ID").iterator().next();
            pubUrl = "https://www.ncbi.nlm.nih.gov/pubmed/" + pubMedId;
        }
        if (record.getAttributes().get("DOI") != null) {
            String doi =  record.getAttributes().get("DOI").iterator().next();
            doi = StringUtils.removeStart(doi, "DOI:"); // remove "DOI:" prefix 
            pubUrl = "https://doi.org/" + doi;
        }
        if (pubUrl != null) {
            feature.setAttribute("url", pubUrl);
        }

        // Overlapping genes
        if (record.getAttributes().get("gene_id") != null) {
            List<String> overlappingGenes = record.getAttributes().get("gene_id");
            String overlappingGenesStr = StringUtils.join(overlappingGenes, ", ");
            if (fieldNotEmpty(overlappingGenesStr)) {
                ArrayList<String> genes = new ArrayList<String>();
                for (String overlappingGene : overlappingGenes) {
                    Item gene = getGene(overlappingGene);
                    genes.add(gene.getIdentifier());
                }
                for (int i = 0; i < genes.size(); i++) {
                    feature.addToCollection("overlappingGenes", genes.get(i));
                }
            }
        }
    }

    /**
     * Get an Item representation of a Gene
     * @param identifier
     * @return
     */
    private Item getGene(String identifier) {
        Item gene = null;
        if (geneItems.containsKey(identifier)) {
            gene = geneItems.get(identifier);
        } else {
            gene = converter.createItem("Gene");
            gene.setAttribute("primaryIdentifier", identifier);
            gene.setReference("organism", getOrganism());
            addItem(gene);
            geneItems.put(identifier, gene);
        }
        return gene;
    }
}
