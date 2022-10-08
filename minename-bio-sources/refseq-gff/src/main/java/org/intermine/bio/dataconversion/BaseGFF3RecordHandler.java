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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.net.URLDecoder;
import java.io.UnsupportedEncodingException;

import org.apache.commons.lang.StringUtils;
import org.intermine.bio.io.gff3.GFF3Record;
import org.intermine.metadata.Model;
import org.intermine.metadata.StringUtil;
import org.intermine.xml.full.Item;

/**
 * A converter/retriever for GFF files. Contains variables and methods common to
 * many other custom GFF sources.
 */

public class BaseGFF3RecordHandler extends GFF3RecordHandler
{
    protected Map<String,String> aliasToRefId = new HashMap<String,String>();
    protected Map<String,String> crossRefToRefId = new HashMap<String,String>();
    protected Map<String,String> geneToRefId = new HashMap<String,String>();
    protected Map<String,String> crossRefSourceIdentifierToDatabaseIdentifierMap = new HashMap<String,String>();

    private static final String ENC = "UTF-8";

    /**
     * Create a new BaseGFF3RecordHandler for the given data model.
     * @param model the model for which items will be created
     */
    public BaseGFF3RecordHandler (Model model) {
        super(model);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(GFF3Record record) {
        Item feature = getFeature();
        String clsName = feature.getClassName();

        // Always set feature source and name
        setFeatureSource(record);
        setFeatureName(record);
    }

    /**
     * Set feature attribute, if present.
     * @param record
     * @param attributeKey
     * @param attributeName
     */
    protected void setFeatureAttribute(GFF3Record record, String attributeKey, String attributeName) {
        Item feature = getFeature();
        if (record.getAttributes().get(attributeKey) != null) {
            String attributeValue = record.getAttributes().get(attributeKey).iterator().next();
            feature.setAttribute(attributeName, attributeValue);
        }
    }

    /**
     * Set feature biotype attribute, if present.
     * @param record
     * @param biotypeKey
     */
    protected void setFeatureBiotype(GFF3Record record, String biotypeKey) {
        setFeatureAttribute(record, biotypeKey, "biotype");
    }

    /**
     * Set feature name attribute, if present.
     * @param record
     * @param symbolKey
     */
    protected void setFeatureName(GFF3Record record) {
        setFeatureAttribute(record, "Name", "name");
    }

    /**
     * Set feature symbol attribute, if present.
     * @param record
     * @param symbolKey
     */
    protected void setFeatureSymbol(GFF3Record record, String symbolKey) {
        setFeatureAttribute(record, symbolKey, "symbol");
    }

    /**
     * Set feature description attribute, if present.
     * @param record
     */
    protected void setFeatureDescription(GFF3Record record) {
        Item feature = getFeature();
        if (record.getAttributes().get("description") != null) {
            String description = record.getAttributes().get("description").iterator().next();
            setItemDescription(feature, description);
        }
    }

    /**
     * Set item description attribute with correct encoding.
     * @param item
     * @param description
     */
    protected void setItemDescription(Item item, String description) {
        try {
            item.setAttribute("description", URLDecoder.decode(URLDecoder.decode(description, ENC), ENC));
        } catch (UnsupportedEncodingException e) {
            System.out.println("WARNING: unsupported encoding " + ENC);
        }
    }

    /**
     * Set feature source.
     * @param record
     */
    protected void setFeatureSource(GFF3Record record) {
        Item feature = getFeature();
        feature.setAttribute("source", record.getSource());
    }

    /**
     * Set feature status attribute if "Ambiguous" or "Frameshift".
     * @param record
     */
    protected void setFeatureStatus(GFF3Record record) {
        Item feature = getFeature();
        if (record.getAttributes().get("feature_type") != null) {
            String ft = record.getAttributes().get("feature_type").iterator().next();
            if (ft.equals("Ambiguous") || ft.equals("Frameshift")) {
                // limiting status to only show if the current gene is of type 'Ambiguous' or type 'Frameshift'
                feature.setAttribute("status", ft);
            }
        }
    }

    /**
     * Handle database cross-references, if present.
     * @param record
     * @param featureIdentifierName
     * @param refPrefix
     */
    protected void handleDbxrefs(GFF3Record record, String featureIdentifierName, String refPrefix) {
        Item feature = getFeature();
        List<String> dbxrefs = record.getDbxrefs();

        if (dbxrefs != null) {
            Iterator<String> dbxrefsIter = dbxrefs.iterator();

            while (dbxrefsIter.hasNext()) {
                String dbxref = dbxrefsIter.next();
                List<String> refList = new ArrayList<String>( Arrays.asList(StringUtil.split(dbxref, ",")));

                for (String ref : refList) {
                    ref = ref.trim();
                    int colonIndex = ref.indexOf(":");

                    if (colonIndex == -1) {
                        throw new RuntimeException("Error in Dbxref attribute " + ref );
                    }

                    if (ref.startsWith(refPrefix)) {
                        feature.setAttribute(featureIdentifierName, ref.replace(refPrefix + ":", ""));
                    }
                }
            }
        }
    }

    /**
     * Create duplicate entities.
     * @param record
     */
    protected void createDuplicateEntities(GFF3Record record) {
        Item feature = getFeature();
        if (record.getAttributes().get("duplicate_entity") != null) {
            String duplicates = record.getAttributes().get("duplicate_entity").iterator().next();
            List<String> entities = new ArrayList<String>(Arrays.asList(StringUtil.split(duplicates, "|")));
            for (String entity : entities) {
                Item duplicateEntityItem = converter.createItem("DuplicateEntity");
                String duplicateEntityItemRefId = duplicateEntityItem.getIdentifier();
                List<String> entityAttributes = new ArrayList<String>(Arrays.asList(StringUtil.split(entity, ",")));
                List<String> locationInformation = new ArrayList<String>(Arrays.asList(StringUtil.split(entityAttributes.get(0), ":")));
                String chromosome = locationInformation.get(0);
                List<String> positionInfo = new ArrayList<String>(Arrays.asList(StringUtil.split(locationInformation.get(1), "..")));
                String start = positionInfo.get(0);
                String end = positionInfo.get(1);
                int strand = locationInformation.get(2).equals("+") ? 1 : -1;

                duplicateEntityItem.setAttribute("chromosome", chromosome);
                duplicateEntityItem.setAttribute("start", start);
                duplicateEntityItem.setAttribute("end", end);
                duplicateEntityItem.setAttribute("strand", Integer.toString(strand));

                if (entityAttributes.size() > 1) {
                    String geneIdentifier = entityAttributes.get(1).replace("NCBI_Gene:", "");
                    duplicateEntityItem.setAttribute("geneIdentifier", geneIdentifier);
                }

                if (entityAttributes.size() > 2) {
                    ArrayList<String> product = new ArrayList<String>(Arrays.asList(StringUtil.split(entityAttributes.get(2), ":")));
                    if (product.size() > 1) {
                        String transcriptId = product.get(1);
                        duplicateEntityItem.setAttribute("transcriptIdentifier", transcriptId);
                    }

                    if (product.size() > 2) {
                        String proteinIdentifier = product.get(2);
                        duplicateEntityItem.setAttribute("proteinIdentifier", proteinIdentifier);
                    }
                }

                try {
                    converter.store(duplicateEntityItem);
                } catch (Exception e) {
                    System.out.println("Exception while storing duplicateEntityItem:" + duplicateEntityItem + "\n" + e);
                }

                feature.addToCollection("duplicateEntities", duplicateEntityItemRefId);
            }
        }
    }
}
