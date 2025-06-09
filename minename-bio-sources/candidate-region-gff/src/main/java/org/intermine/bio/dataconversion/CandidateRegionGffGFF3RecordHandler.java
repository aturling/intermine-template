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
    private HashMap<String, Item> geneItems = new HashMap<String, Item>();

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

        setFeatureAttribute(record, "breed", "breed");
        setFeatureAttribute(record, "breed_class", "breedClass");
        setFeatureAttribute(record, "breed_origin", "breedOrigin");
        setFeatureAttribute(record, "stat_test", "statTest");

        // Statistics value (float)
        if (record.getAttributes().get("stat_value") != null) {
            String fieldValue = record.getAttributes().get("stat_value").iterator().next();
            // Additional formatting may be required for floats:
            fieldValue = formatFloatField(fieldValue);
            if (fieldNotEmpty(fieldValue)) {
                feature.setAttribute("statValue", fieldValue);
            }
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
