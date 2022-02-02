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

import java.io.DataOutputStream;
import javax.net.ssl.HttpsURLConnection;

/**
 * Extend update-publications source by adding NCBI API key to URL.
 * @author
 */
public class EntrezPublicationsKeyRetriever extends EntrezPublicationsRetriever
{
    protected static final String PROP_KEY = "ncbi.eutils.apiKey";
    private boolean loadFullRecord = false;

    /**
     * Load summary version of Publication record by default. If this boolean (loadFullRecord)
     * is true, load all data, eg. abstract, MeSH terms etc.
     *
     * @param loadFullRecord if TRUE load full record of publication.
     */
    public void setLoadFullRecord(String loadFullRecord) {
        super.setLoadFullRecord(loadFullRecord);
        System.out.println("Setting loadFullRecord to: " + loadFullRecord + " in child class");
        if ("true".equalsIgnoreCase(loadFullRecord)) {
            this.loadFullRecord = true;
        }
    }

    /**
     * Obtain the pubmed esummary information for the publications
     * override URL to add API key as parameter
     * @param ids the pubMedIds of the publications
     * @return a Reader for the information
     * @throws Exception if an error occurs
     */
    @Override
    protected Reader getReader(Set<Integer> ids) throws Exception {
        /**
         * Fix - Use HTTP POST instead of HTTP GET method for uploading
         * Pubmed Ids
         * Author: Norbert Auer
         * e-mail: norbert.auer@boku.ac.at
         */

        String urlString = ESUMMARY_URL;
        if (loadFullRecord) {
            urlString = EFETCH_URL;
        }
        URL obj = new URL(urlString);
        HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

        // add request header to POST
        con.setRequestMethod("POST");

        // con.setRequestProperty("User-Agent", USER_AGENT);
        con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

        // Use API key if present
        String entrezApiKey = PropertiesUtil.getProperties().getProperty(PROP_KEY);
        String urlParameters = "tool=intermine&db=pubmed&rettype=abstract&retmode=xml";
        if (entrezApiKey != null) {
            urlParameters += "&api_key=" + entrezApiKey;
        }
        urlParameters += "&id=" + StringUtil.join(ids, ",");
        //String urlParameters = "tool=intermine&db=pubmed&rettype=abstract&retmode=xml&id="
        //        + StringUtil.join(ids, ",");

        // Send post request
        con.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        wr.writeBytes(urlParameters);
        wr.flush();
        wr.close();

        int responseCode = con.getResponseCode();

        return new BufferedReader(new InputStreamReader(con.getInputStream()));
    }
}
