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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.intermine.metadata.StringUtil;
import org.intermine.util.PropertiesUtil;

/**
 * Extend entrez-organism source by adding NCBI API key to URL.
 * @author
 */
public class EntrezOrganismKeyRetriever extends EntrezOrganismRetriever
{
    protected static final Logger LOG = Logger.getLogger(EntrezOrganismKeyRetriever.class);
    protected static final String BASE_URL =
        "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi";
    protected static final String PROP_KEY = "ncbi.eutils.apiKey";

    /**
     * Obtain the pubmed esummary information for the organisms
     * Override to add API key to URL
     * @param ids the taxon ids of the organisms
     * @return a Reader for the information
     * @throws Exception if an error occurs
     */
    @Override
    protected Reader getReader(Set<String> ids) throws Exception {
        //URL url = new URL(ESUMMARY_URL + StringUtil.join(ids, ","));
        URL url = new URL(getEsummaryURL() + StringUtil.join(ids, ","));
        return new BufferedReader(new InputStreamReader(url.openStream()));
    }

    /**
     * Override to add API key to UR
     * @param id organism id
     * @return reader
     * @throws Exception if something goes wrong
     */
    protected static Reader getReader(Integer id) throws Exception {
        //URL url = new URL(ESUMMARY_URL + id);
        URL url = new URL(getEsummaryURL() + id);
        return new BufferedReader(new InputStreamReader(url.openStream()));
    }

    /**
     * Build the esummary URL with parameters including API key
     * @return String esummary URL with parameters
     */
    private static String getEsummaryURL() {
        String url = BASE_URL + "?db=taxonomy&retmode=xml";
        // Use API key in url if present
        String entrezApiKey = PropertiesUtil.getProperties().getProperty(PROP_KEY);
        if (entrezApiKey != null) {
            url += "&api_key=" + entrezApiKey;
        }
        url += "&id=";
        return url;
    }
}
