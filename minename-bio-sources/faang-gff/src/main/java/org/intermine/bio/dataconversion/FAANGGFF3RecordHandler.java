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

import org.intermine.bio.io.gff3.GFF3Record;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;

/**
 * A converter/retriever for the FAANG GFF dataset via GFF files.
 */

public class FAANGGFF3RecordHandler extends BaseGFF3RecordHandler
{
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
    }
}
