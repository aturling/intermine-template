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

/**
 * A converter/retriever for the FAANG GFF dataset via GFF files.
 */

public class FAANGGFF3RecordHandler extends BaseGFF3RecordHandler
{
    private HashMap<String, Item> analysisItems = new HashMap<String, Item>();
    
    /**
     * Create a new FAANGGFF3RecordHandler for the given data model.
     * @param model the model for which items will be created
     */
    public FAANGGFF3RecordHandler (Model model) {
        super(model);

	// No parent-child relationships in FAANG GFF files
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(GFF3Record record) {
        super.process(record);

        Item feature = getFeature();

	// Set reference to Analysis (via source field)
	String analysisSrc = record.getSource();
        Item analysis = getAnalysis(analysisSrc);
        feature.setReference("analysis", analysis.getIdentifier());	
    }

    /**
     * Get an Item representation of an Analysis item
     *
     * @param analysisSrc Analysis source
     * @return Analysis item
     */
    protected Item getAnalysis(String analysisSrc) {
        Item analysis = null;
        if (analysisItems.containsKey(analysisSrc)) {
            analysis = analysisItems.get(analysisSrc);
        } else {
            analysis = converter.createItem("Analysis");
            analysis.setAttribute("source", analysisSrc);
	    analysis.setReference("organism", getOrganism());
	    addItem(analysis);
            analysisItems.put(analysisSrc, analysis);
        }
        return analysis;
    }
}
