package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2021 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.Reader;

import org.apache.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;

/**
 * DataConverter to load KEGG Pathways with url field and link them to Genes
 *
 * @author Xavier Watkins
 * @author
 */
public class KeggPathwayUrlConverter extends BasePathwayConverter
{
    protected static final Logger LOG = Logger.getLogger(KeggPathwayUrlConverter.class);
    private static final String PROP_FILE = "kegg-pathway-url_config.properties";
    private static final String DATASET_TITLE = "KEGG pathways data set";
    private static final String DATA_SOURCE_NAME = "GenomeNet";

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public KeggPathwayUrlConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
        readConfig(PROP_FILE);
    }
}
