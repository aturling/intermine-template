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
 * A converter/retriever for the Maize GFF dataset.
 */

public class MaizeCommunityGFF3RecordHandler extends MaizeGFF3RecordHandler
{
    // Map of attribute name -> field name for feature fields to store (strings)
    private static final HashMap<String, String> attributesToSet = new HashMap<String, String>();
    private HashMap<String, Item> imageItems = new HashMap<String, Item>();

    static {
        // Attribute name -> corresponding feature field name
        attributesToSet.put("GeneticBackground", "geneticBackground");
        attributesToSet.put("Stock", "stock");
        attributesToSet.put("TaggedGene", "taggedGene");
    }

    /**
     * Create a new MaizeCommunityGFF3RecordHandler for the given data model.
     * @param model the model for which items will be created
     */
    public MaizeCommunityGFF3RecordHandler (Model model) {
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

        if (clsName.equals("TransposableElementInsertionSite")) {
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

            // Special case: images
            if (record.getAttributes().get("Image") != null) {
                List<String> images = record.getAttributes().get("Image");
                for (String image : images) {
                    if (fieldNotEmpty(image)) {
                        Item imageItem = getImage(image);
                        feature.addToCollection("images", imageItem.getIdentifier());
                    }
                }
            }
        }
    }

    /**
     * Get an Item representation of an Image
     * @param image
     * @return
     */
    private Item getImage(String image) {
        Item imageItem = null;
        // Use the last part of the url (the image filename) as the primary id
        String imageId = StringUtils.substringBefore(image.substring(image.lastIndexOf('/') + 1), ".");
        if (imageItems.containsKey(imageId)) {
            imageItem = imageItems.get(imageId);
        } else {
            imageItem = converter.createItem("Image");
            imageItem.setAttribute("primaryIdentifier", imageId);
            imageItem.setAttribute("url", image);
            try {
                converter.store(imageItem);
            } catch (Exception e) {
                System.out.println("Exception while storing imageItem:" + imageItem + "\n" + e);
            }
            imageItems.put(imageId, imageItem);
        }
        return imageItem;
    }

    /**
     * Return true if field has a nonempty value
     */
    private boolean fieldNotEmpty(String fieldValue) {
        // Consider "-" or "None" to be empty / no value
        if ("-".equals(fieldValue)) {
            return false;
        } else if ("None".equals(fieldValue)) {
            return false;
        }

        return StringUtils.isNotEmpty(fieldValue);
    }
}