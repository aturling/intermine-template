package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2024 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;
import org.intermine.xml.full.ReferenceList;

/**
 * Load reactions and link them to genes
 * (Based off of KEGG-pathway loader)
 *
 * @author
 */
public class GpluseReactionsConverter extends BioFileConverter
{
    private static final String DATASET_TITLE = "GplusE reactions data set";
    private static final String DATA_SOURCE_NAME = "GplusE";
    protected static final Logger LOG = Logger.getLogger(GpluseReactionsConverter.class);

    private Map<String, Item> geneItems = new HashMap<String, Item>();
    protected Map<String, String> reactionIdentifiers = new HashMap<String, String>();
    protected Map<String, Item> reactionsNotStored = new HashMap<String, Item>();

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public GpluseReactionsConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        Iterator<?> lineIter = FormattedTextParser.parseTabDelimitedReader(reader);
        File currentFile = getCurrentFile();

        while (lineIter.hasNext()) {
            String[] line = (String[]) lineIter.next();
            Pattern filePattern = Pattern.compile("^(\\S+)_reactions.*");
            Matcher matcher = filePattern.matcher(currentFile.getName());
            if (line.length <= 1 || line[0].startsWith("#")) {
                continue;
            }
            if (currentFile.getName().startsWith("reaction_title")) {
                processReaction(line);
            } else if (matcher.find()) {
                String taxonId = matcher.group(1);

                if (taxonId != null && taxonId.length() != 0) {
                    String geneId = line[0];
                    String mapIdentifiers = line[1];
                    ReferenceList referenceList = new ReferenceList("reactions");
                    String [] mapArray = mapIdentifiers.split(" ");
                    for (int i = 0; i < mapArray.length; i++) {
                        String identifier = mapArray[i];
                        String refId = reactionIdentifiers.get(identifier);
                        if (refId == null) {
                            Item item = getReaction(identifier);
                            refId = item.getIdentifier();
                            reactionsNotStored.put(identifier, item);
                        }
                        referenceList.addRefId(refId);
                    }
                    getGene(geneId, taxonId, referenceList);
                }
            }
        }
    }
    
    /**
     * {@inheritDoc}
     */
    public void close() throws ObjectStoreException {
        for (Item reaction : reactionsNotStored.values()) {
            store(reaction);
        }
        reactionsNotStored.clear();
    }

    private Item getGene(String geneId, String taxonId, ReferenceList referenceList)
        throws ObjectStoreException {
        Item gene = geneItems.get(geneId);
        if (gene == null) {
            gene = createItem("Gene");
            gene.setAttribute("primaryIdentifier", geneId);
            gene.setReference("organism", getOrganism(taxonId));
            gene.addCollection(referenceList);
            geneItems.put(geneId, gene);
            store(gene);
        }
        return gene;
    }

    private void processReaction(String[] line) throws ObjectStoreException {
        String identifier = line[0];
        String name = line[1];
        String subsystem = line[2];
        String description = null;
        if (line.length > 3) {
            description = line[3];
        }

        Item reaction = reactionsNotStored.remove(identifier);
        if (reaction == null) {
            reaction = getReaction(identifier);
        }
        reaction.setAttribute("name", name);
        if (StringUtils.isNotEmpty(description)) {
            reaction.setAttribute("description", description);
        }
        store(reaction);
    }

    private Item getReaction(String identifier) {
        Item item = createItem("Reaction");
        item.setAttribute("identifier", identifier);
        reactionIdentifiers.put(identifier, item.getIdentifier());
        return item;
    }
}
