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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.BuildException;
import org.intermine.metadata.TypeUtil;
import org.intermine.model.InterMineObject;
import org.intermine.model.bio.AliasName;
import org.intermine.model.bio.BioEntity;
import org.intermine.model.bio.Chromosome;
import org.intermine.model.bio.Consequence;
import org.intermine.model.bio.ConsequenceType;
import org.intermine.model.bio.DataSet;
import org.intermine.model.bio.DataSource;
import org.intermine.model.bio.Deletion;
import org.intermine.model.bio.Delins;
import org.intermine.model.bio.Indel;
import org.intermine.model.bio.Insertion;
import org.intermine.model.bio.Gene;
import org.intermine.model.bio.Location;
import org.intermine.model.bio.MNV;
import org.intermine.model.bio.Ontology;
import org.intermine.model.bio.Organism;
import org.intermine.model.bio.SequenceAlteration;
import org.intermine.model.bio.SequenceFeature;
import org.intermine.model.bio.SNV;
import org.intermine.model.bio.SOTerm;
import org.intermine.model.bio.Transcript;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.ObjectStoreWriter;
import org.intermine.objectstore.proxy.ProxyReference;
import org.intermine.objectstore.query.ConstraintSet;
import org.intermine.metadata.ConstraintOp;
import org.intermine.metadata.StringUtil;
import org.intermine.objectstore.query.ContainsConstraint;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.QueryField;
import org.intermine.objectstore.query.QueryObjectReference;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryValue;
import org.intermine.objectstore.query.Results;
import org.intermine.objectstore.query.ResultsRow;
import org.intermine.objectstore.query.SimpleConstraint;
import org.intermine.task.FileDirectDataLoaderTask;
import org.intermine.util.FormattedTextParser;

/**
 * Loader for SNP
 *
 * @author
 */
public class SNPVariationLoaderTask extends FileDirectDataLoaderTask
{
    private static final Logger LOG = Logger.getLogger(SNPVariationLoaderTask.class);
    private static final String[] EXPECTED_HEADERS = {
        "#CHROM",
        "POS",
        "ID",
        "REF",
        "ALT",
        "QUAL",
        "FILTER",
        "INFO"
    };
    private static final String VARIANT_ANNOTATION_SOURCE = "Ensembl VEP";
    private static final ArrayList<String> FUNCTION_CLASS_TO_IGNORE = new ArrayList<String>(Arrays.asList("downstream_gene_variant", "upstream_gene_variant", "intergenic_variant", "intron_variant"));

    private String taxonId = null;
    private String assemblyVersion = null;
    private Organism organism = null;
    private String dataSourceName = null;
    private String dataSetTitle = null;
    private DataSet dataSet = null;
    private DataSource dataSource = null;
    private Ontology ontology = null;
    private String geneSource = null;

    private HashSet<Transcript> transcriptSet = new HashSet<Transcript>();
    private HashSet<Consequence> consequenceSet = new HashSet<Consequence>();
    private HashSet<String> previousGeneSet = new HashSet<String>();
    private HashSet<String> seenSet = new HashSet<String>();

    private Map<String, Consequence> consequences = new HashMap<String, Consequence>();
    private Map<Transcript, HashSet<SequenceAlteration>> transcriptToSequenceAlterationMap = new HashMap<Transcript, HashSet<SequenceAlteration>>();
    private Map<String, SOTerm> createdSotermMap = new HashMap<String, SOTerm>();
    private Map<String, Gene> createdGeneMap = new HashMap<String, Gene>();
    private Map<String, AliasName> createdAliasNameMap = new HashMap<String, AliasName>();
    private Map<String, Chromosome> createdChromosomeMap = new HashMap<String, Chromosome>();
    private Map<String, Transcript> createdTranscriptMap = new HashMap<String, Transcript>();
    private Map<String, ConsequenceType> consequenceTypeMap = new HashMap<String, ConsequenceType>();
    private HashMap<Integer, InterMineObject> imoTracker = new HashMap<Integer, InterMineObject>();

    //Set this if we want to do some testing...
    private File[] files = null;
    private static final String NAMESPACE = "org.intermine.model.bio";

    /**
     * Sets the taxon ID for features in this file.
     *
     * @param taxonId a single taxon Id
     */
    public void setTaxonId(String taxonId) {
        this.taxonId = taxonId;
    }

    /**
     * Sets the data set title.
     *
     * @param dataSetTitle data set title
     */
    public void setDataSetTitle(String dataSetTitle) {
        this.dataSetTitle = dataSetTitle;
    }

    /**
     * Sets the data source name.
     *
     * @param dataSourceName data source name
     */
    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    /**
     * Set the assembly version for chromosomes.
     * @param assemblyVersion assembly version
     */
    public void setAssemblyVersion(String assemblyVersion) {
        this.assemblyVersion = assemblyVersion;
    }

    /**
     * Set the gene source for genes.
     * @param geneSource gene source
     */
    public void setGeneSource(String geneSource) {
        this.geneSource = geneSource;
    }

    /**
     * Process and load the SNP file.
     */
    @Override
    public void process() {
        try {
            ObjectStoreWriter osw = getIntegrationWriter();
            if (!osw.isInTransaction()) {
                osw.beginTransaction();
            }
            super.process();
            if (!osw.isInTransaction()) {
                osw.commitTransaction();
            }
            getDirectDataLoader().close();
        } catch (ObjectStoreException e) {
            throw new BuildException("failed to store object", e);
        }
    }

    @Override
    public void execute() {
        // don't configure dynamic attributes if this is a unit test!
        if (getProject() != null) {
            configureDynamicAttributes(this);
        }
        if (files != null) {
            // setFiles() is used only for testing
            for (int i = 0; i < files.length; i++) {
                processFile(files[i]);
            }
            try {
                getDirectDataLoader().close();
            } catch (ObjectStoreException e) {
                throw new BuildException("Failed closing DirectDataLoader", e);
            }
        } else {
            // this will call processFile() for each file
            super.execute();
        }
    }

    /**
     * Called by parent process() method for each file found
     *
     * {@inheritDoc}
     */
    public void processFile(File file) {
        System.out.println("Processing file: " + file);
        LOG.info("Processing file: " + file);

        if (taxonId == null) {
            throw new BuildException("taxonId must be set in project.xml");
        }

        FileReader fileReader;
        try {
            fileReader = new FileReader(file);
            BufferedReader reader = new BufferedReader(fileReader);

            Iterator lineIter = FormattedTextParser.parseTabDelimitedReader(reader);
            long counter = 0;
            while (lineIter.hasNext()) {
                String[] line = (String[]) lineIter.next();
                counter++;
                if (!processVcfEntry(line)) {
                    System.out.println("Error processing line:");
                    printLine(line);
                    return;
                }

                if ((counter % 100000) == 0) {
                    System.out.println("Processed " + counter + " lines...");
                    LOG.info("Processed " + counter + " lines...");
                }
            }
        } catch (FileNotFoundException e) {
            throw new BuildException("problem reading file - file not found: " + file, e);
        } catch (IOException e) {
            throw new BuildException("error while closing FileReader for: " + file, e);
        } catch (ObjectStoreException e) {
            throw new BuildException("error while creating objects: " + file, e);
        } catch (Exception e) {
            e.printStackTrace();
            throw new BuildException("cannot parse file: " + file, e);
        }

        // storing all delayed entities
        if (transcriptToSequenceAlterationMap.size() > 0) {
            System.out.println("Doing bulk store");
            try {
                bulkStore();
            } catch (Exception e) {
                System.out.println(e);
            }
        }

        if (imoTracker.size() > 0) {
            // sanity check
            System.out.println("Number of entities left behind: " + imoTracker.size());
            System.out.println("Check logs");
            for (Map.Entry<Integer, InterMineObject> entry : imoTracker.entrySet()) {
                LOG.info("ID: " + entry.getKey() + " <=> " + entry.getValue());
            }
        }
    }

    /**
     * Process VCF entry
     * @param fields
     * @return boolean
     * @throws ObjectStoreException
     */
    private boolean processVcfEntry(String[] fields) throws ObjectStoreException {
        if (fields.length < EXPECTED_HEADERS.length) {
            System.out.println("Fields length of " + fields.length + " vs Expected length of " + EXPECTED_HEADERS.length);
            throw new BuildException("unexpected number of columns in VCF file");
        }

        String chromosomeIdentifier = fields[0];
        Integer position = new Integer(fields[1]);
        String id = fields[2];
        String ref = fields[3];
        String alt = fields[4];
        String info = fields[7];
        ArrayList<String> infoElements = new ArrayList<String>(Arrays.asList(StringUtil.split(info, ";")));

        Chromosome chromosome = getChromosome(chromosomeIdentifier);
        chromosome.setAssembly(assemblyVersion);

        String type = getKeyValuePair(infoElements, "TSA=").get(1);
        ArrayList<String> aliases = getKeyValuePair(infoElements, "alias=");
        ArrayList<String> csq = getKeyValuePair(infoElements, "CSQ=");
        ArrayList<HashSet> returnObject = new ArrayList<HashSet>();
        HashSet<Consequence> consequenceSet = new HashSet<Consequence>();
        HashSet<Transcript> transcriptSet = new HashSet<Transcript>();

        boolean storeFeature = true;
        String saClassName, soTerm, saName;

        if (type.toUpperCase().equals("SNV")) {
            saClassName = "SNV";
            soTerm = "SNV";
            saName = "SNV";
        } else if (type.toUpperCase().equals("INDEL")) {
            saClassName = "Indel";
            soTerm = "indel";
            saName = "INDEL";
        } else if (type.toUpperCase().equals("INSERTION")) {
            saClassName = "Insertion";
            soTerm = "insertion";
            saName = "INSERTION";
        } else if (type.toUpperCase().equals("DELETION")) {
            saClassName = "Deletion";
            soTerm = "deletion";
            saName = "DELETION";
        } else if (type.toUpperCase().equals("MNV")) {
            saClassName = "MNV";
            soTerm = "MNV";
            saName = "MNV";
        } else if (type.toUpperCase().equals("DELINS")) {
            saClassName = "Delins";
            soTerm = "delins";
            saName = "DELINS";
        } else if (type.toUpperCase().equals("SEQUENCE_ALTERATION")) {
            saClassName = "SequenceAlteration";
            soTerm = "sequence_alteration";
            saName = "SEQUENCE_ALTERATION";
        } else if (type.toUpperCase().equals("TANDEM_REPEAT")) {
            saClassName = "TandemRepeat";
            soTerm = "tandem_repeat";
            saName = "TANDEM_REPEAT";
        } else if (type.toUpperCase().equals("SUBSTITUTION")) {
            saClassName = "Substitution";
            soTerm = "substitution";
            saName = "SUBSTITUTION";
        } else {
            throw new RuntimeException("Unexpected feature of type: " + type);
        }

        String className =  TypeUtil.generateClassName(NAMESPACE, saClassName);
        Class<? extends InterMineObject> imClass;
        Class<?> c;
        try {
            c = Class.forName(className);
            if (InterMineObject.class.isAssignableFrom(c)) {
                imClass = (Class<? extends InterMineObject>) c;
            } else {
                throw new RuntimeException("Feature className must be a valid class in the "
                        + "model that inherits from InterMineObject, but was: " + className);
            }
        } catch (ClassNotFoundException e1) {
            throw new BuildException("unknown class: " + className
                    + " while creating new SequenceAlteration object");
        }
        SequenceAlteration snp
            = (SequenceAlteration) getDirectDataLoader().createObject(imClass);

        snp.setPrimaryIdentifier(id);
        imoTracker.put(snp.getId(), snp);
        snp.setName(saName);
        snp.setSource(geneSource);
        snp.setSequenceOntologyTerm(getSoTerm(soTerm));
        snp.setOrganism(getOrganism());
        if (saClassName.equals("SNV")) {
            snp.setLength(1);
        }
        snp.setReferenceAllele(ref);
        snp.setAlternateAllele(alt);
        snp.setChromosome(chromosome);
        snp.addDataSets(getDataSet());
        //snp.setDataSets(new HashSet<DataSet>(Arrays.asList(getDataSet())));

        // Processing ssIds corresponding to each SNP ID
        Set<AliasName> aliasSet = null;
        if (aliases != null) {
            aliasSet = processSNPssIdentifiers((SequenceAlteration) snp, aliases.get(1));
            if (aliasSet.size() > 0) {
                // set alias collection
                snp.setAliases(aliasSet);
            }
        }

        // All variants in the SNP VCF are w.r.t. the + strand
        Location location = createLocation(chromosome, snp, position, ref, alt, "1");
        snp.setChromosomeLocation(location);

        // Processing consequences and transcripts
        if (csq != null) {
            returnObject = processCSQ((SequenceAlteration) snp, csq.get(1));
            consequenceSet = returnObject.get(0);
            transcriptSet = returnObject.get(1);
            if (consequenceSet != null) {
                // set consequences collection
                snp.setConsequences(consequenceSet);
            }
            if (transcriptSet != null) {
                // set transcripts collection
                snp.setTranscripts(transcriptSet);
                for (Transcript transcript : transcriptSet) {
                    // keeping track of all SNPs that have altered a transcript
                    if (transcriptToSequenceAlterationMap.containsKey(transcript)) {
                        // if there is even one transcript from a previous entry
                        // do not attempt a store for current SNP
                        storeFeature = false;
                        transcriptToSequenceAlterationMap.get(transcript).add(snp);
                    } else {
                        transcriptToSequenceAlterationMap.put(transcript, new HashSet<SequenceAlteration> (Arrays.asList(snp)));
                        storeFeature = false;
                    }
                }
            }
        }

        if (storeFeature) {
            try {
                getDirectDataLoader().store(snp);
                imoTracker.remove(snp.getId());
            } catch (ObjectStoreException e) {
                throw new BuildException("Problem storing " + saClassName + " : ", e);
            }
        }

        return true;
    }


    /**
     * Stores all delayed entities
     * @throws ObjectStoreException
     */
    private void bulkStore() throws ObjectStoreException {
        for (Map.Entry<Transcript, HashSet<SequenceAlteration>> entry : transcriptToSequenceAlterationMap.entrySet()) {
            Transcript transcript = entry.getKey();
            // set Transcript -> sequenceAlterations collection
            transcript.setSequenceAlterations(entry.getValue());

            for (SequenceAlteration sa : entry.getValue()) {
                if (seenSet.contains(String.valueOf(sa.getId()))) {
                    if (imoTracker.containsKey(sa.getId())) {
                        System.out.println("imoTracker has sequenceAlteration even after its been stored: " + sa);
                    }
                    continue;
                } else {
                    // store sequence alteration since it was never seen before
                    getDirectDataLoader().store(sa);
                    imoTracker.remove(sa.getId());
                    seenSet.add(String.valueOf(sa.getId()));
                }
            }

            getDirectDataLoader().store(transcript);
            imoTracker.remove(transcript.getId());
        }
        transcriptToSequenceAlterationMap.clear();
    }

    /**
     * Creates a set of AliasName entities for a given series of ssIds
     * @param saFeature
     * @param ssIds
     * @return set of <AliasName>
     * @throws ObjectStoreException
     */
    private Set<AliasName> processSNPssIdentifiers(SequenceAlteration saFeature, String ssIds) throws ObjectStoreException {
        // Note: The genomic_model will have to be altered where reference to AliasName is now in BioEntity instead of Gene
        Set<AliasName> setOfSsIdObjects = new HashSet<AliasName>();
        String[] aliases = ssIds.split(",");
        for (int i = 0; i < aliases.length; i++) {
            String[] eachAlias = aliases[i].split(":");
            String ssId = eachAlias[0];
            String ssIdSource = eachAlias[1];
            AliasName alias = getAliasName(ssId, ssIdSource);
            setOfSsIdObjects.add(alias);
        }
        return setOfSsIdObjects;
    }

    // NOT USED
    ///**
    // * Creates a set of AliasName entities for a given series of probeIds
    // * @param saFeature
    // * @param probeIds
    // * @return set of <AliasName>
    // * @throws ObjectStoreException
    // */
    /*
    private Set<AliasName> processSnpArrayProbeIdentifiers(SequenceAlteration saFeature, String probeIds) throws ObjectStoreException {
        Set<AliasName> setOfProbeIdObjects = new HashSet<AliasName>();
        String[] probes = probeIds.split(",");
        for (int i = 0; i < probes.length; i++) {
            String[] eachProbe = probes[i].split(":");
            String probeId = eachProbe[0];
            String probeSource = eachProbe[1];
            AliasName alias = getDirectDataLoader().createObject(AliasName.class);
            imoTracker.put(alias.getId(), alias);
            alias.setIdentifier(probeId);
            alias.setSource(probeSource);
            alias.setOrganism(getOrganism());
            // AliasName -> feature reference
            alias.setFeatures(new HashSet<SequenceFeature>(Arrays.asList((SequenceFeature) saFeature)));
            getDirectDataLoader().store(alias);
            imoTracker.remove(alias.getId());
            setOfProbeIdObjects.add(alias);
        }
        return setOfProbeIdObjects;
    }
    */

    /**
     * Processes variant effects
     * @param saFeature
     * @param variantEffects
     * @return
     * @throws ObjectStoreException
     */
    private ArrayList <HashSet> processCSQ(SequenceAlteration saFeature, String csq) throws ObjectStoreException {
        String[] csqs = csq.split(",");
        HashSet<Consequence> consequenceSet = new HashSet<Consequence>();
        HashSet<Transcript> transcriptSet = new HashSet<Transcript>();

        for (int i = 0; i < csqs.length; i++) {
            // order of info: Alternate Allele|Consequence Type|SO Term|Transcript ID|Residue|Sift
            // not all fields always present
            HashSet<ConsequenceType> consequenceTypeSet = new HashSet<ConsequenceType>();
            String[] csqInfo = csqs[i].split("\\|");

            //if (FUNCTION_CLASS_TO_IGNORE.contains(annotationInfo[3])) {
            //    continue;
            //} else {
            //    String[] consequenceTypes = annotationInfo[3].split("\\|");
            //    for (String consequenceType : consequenceTypes) {
            //        consequenceTypeSet.add(getConsequence(consequenceType));
            //    }
            //}

            Consequence consequence = getDirectDataLoader().createObject(Consequence.class);
            imoTracker.put(consequence.getId(), consequence);
            consequence.setSnpId(saFeature.getPrimaryIdentifier());

            // set Consequence -> consequenceType collection
            // TODO: Issue on the webapp while trying to access consequenceType
            consequence.setConsequenceTypes(consequenceTypeSet);

            if (csqInfo.length > 1) {
                // No longer setting alternate codon - removed from model
                //consequence.setAlternateCodon(csqInfo[0]);
                consequence.setAlternateAllele(csqInfo[0]);
                consequenceTypeSet.add(getConsequence(csqInfo[1]));

                if(csqInfo.length > 3) {
                    consequence.setTranscriptIdentifier(csqInfo[3]);
                    Transcript transcript = getTranscript(csqInfo[3]);
                    consequence.setTranscript(transcript);
                    transcriptSet.add(transcript);
                }

                if (csqInfo.length > 4 && csqInfo[4] != null) {
                    String[] residue = csqInfo[4].split("\\/");
                    consequence.setReferenceResidue(residue[0]);
                    consequence.setAlternateResidue(residue[1]);
                }

                if (csqInfo.length > 5) {
                    String[] sift = csqInfo[5].split("\\(");
                    //sift[1] = sift[1].split("\\)", "");
                    consequence.setSiftQualitativePrediction(sift[0].replaceAll("-_", ""));
                    consequence.setSiftNumericalValue(sift[1].replaceAll("[()]", ""));
                }
            }

            // set Consequence -> variant reference
            consequence.setVariant(saFeature);
            getDirectDataLoader().store(consequence);
            imoTracker.remove(consequence.getId());
            consequenceSet.add(consequence);
        }
        return new ArrayList<HashSet> (Arrays.asList(consequenceSet, transcriptSet));
    }

    /**
     * Creates a Location entity for a given set of location information
     * @param locatedOn
     * @param feature
     * @param start
     * @param refAllele
     * @param altAllele
     * @param strand
     * @throws ObjectStoreException
     */
    private Location createLocation(Chromosome locatedOn, SequenceAlteration feature, int start, String refAllele, String altAllele, String strand) throws ObjectStoreException {
        int length = 0;
        Location location = getDirectDataLoader().createObject(Location.class);
        imoTracker.put(location.getId(), location);
        location.setLocatedOn(locatedOn);
        location.setFeature((BioEntity) feature);
        location.addDataSets(getDataSet());
        length = refAllele.length();

        int end = (start + length) - 1;

        if (start <= end) {
            location.setStart(start);
            location.setEnd(end);
        } else {
            System.out.println("Trying to create a Location entity where start > end");
            System.exit(1);
        }

        location.setStrand(strand);
        // Annotating Location entity such that it is not considered when computing overlaps during post-process
        location.setDoNotComputeOverlaps("Y");
        getDirectDataLoader().store(location);
        imoTracker.remove(location.getId());
        return location;
    }

    /**
     * For a given tag, returns its corresponding value
     * @param elements
     * @param tag
     * @return
     */
    private ArrayList<String> getKeyValuePair(ArrayList<String> elements, String tag) {
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i).contains(tag)) {
                return new ArrayList<String>(Arrays.asList(StringUtil.split(elements.get(i), "=")));
            }
        }
        return null;
    }

    /**
     * Returns the current DataSource
     * @return
     * @throws ObjectStoreException
     */
    private DataSource getDataSource() throws ObjectStoreException {
        if (dataSource == null) {
            if (StringUtils.isEmpty(dataSourceName)) {
                throw new RuntimeException("Data source name not set in project.xml");
            }
            dataSource = getDirectDataLoader().createObject(DataSource.class);
            imoTracker.put(dataSource.getId(), dataSource);
            dataSource.setName(dataSourceName);
            getDirectDataLoader().store(dataSource);
            imoTracker.remove(dataSource.getId());
        }
        return dataSource;
    }

    /**
     * Returns the current DataSet
     * @return
     * @throws ObjectStoreException
     */
    private DataSet getDataSet() throws ObjectStoreException {
        if (dataSet == null) {
            if (StringUtils.isEmpty(dataSetTitle)) {
                throw new RuntimeException("Data set title not set in project.xml");
            }
            dataSet = getDirectDataLoader().createObject(DataSet.class);
            imoTracker.put(dataSet.getId(), dataSet);
            dataSet.setName(dataSetTitle);
            dataSet.setDataSource(getDataSource());
            getDirectDataLoader().store(dataSet);
            imoTracker.remove(dataSet.getId());
        }
        return dataSet;
    }

    /**
     * Returns the current Organism
     * @return organism
     * @throws ObjectStoreException
     */
    private Organism getOrganism() throws ObjectStoreException {
        if (organism == null) {
            organism = getDirectDataLoader().createObject(Organism.class);
            imoTracker.put(organism.getId(), organism);
            organism.setTaxonId(taxonId);
            getDirectDataLoader().store(organism);
            imoTracker.remove(organism.getId());
        }
        return organism;
    }

    /**
     * Returns a ConsequenceType entity for a given consequence type
     * @param consequenceTypeString
     * @return
     * @throws ObjectStoreException
     */
    private ConsequenceType getConsequence(String consequenceTypeString) throws ObjectStoreException {
        ConsequenceType consequenceType;
        if (consequenceTypeMap.containsKey(consequenceTypeString)) {
            consequenceType = consequenceTypeMap.get(consequenceTypeString);
        } else {
            consequenceType = getDirectDataLoader().createObject(ConsequenceType.class);
            consequenceType.setName(consequenceTypeString.toUpperCase());
            getDirectDataLoader().store(consequenceType);
            consequenceTypeMap.put(consequenceTypeString, consequenceType);
        }
        return consequenceType;
    }

    /**
     * For a given identifier, returns a Transcript entity
     * @param identifier
     * @return
     * @throws ObjectStoreException
     */
    private Transcript getTranscript(String identifier) throws ObjectStoreException {
        Transcript transcript;
        if (createdTranscriptMap.containsKey(identifier)) {
            transcript = createdTranscriptMap.get(identifier);
        } else {
            transcript = getDirectDataLoader().createObject(Transcript.class);
            transcript.setSequenceOntologyTerm(getSoTerm("transcript"));
            transcript.setPrimaryIdentifier(identifier);
            transcript.setSource(geneSource);
            transcript.setOrganism(getOrganism());
            transcript.addDataSets(getDataSet());
            imoTracker.put(transcript.getId(), transcript);
            createdTranscriptMap.put(identifier, transcript);
        }
        return transcript;
    }

    /**
     * For a given identifier, returns a Chromosome entity
     * @param identifier
     * @return
     * @throws ObjectStoreException
     */
    private Chromosome getChromosome(String identifier) throws ObjectStoreException {
        Chromosome chr;
        if (createdChromosomeMap.containsKey(identifier)) {
            chr = createdChromosomeMap.get(identifier);
        } else {
            chr = getDirectDataLoader().createObject(Chromosome.class);
            imoTracker.put(chr.getId(), chr);
            chr.setPrimaryIdentifier(identifier);
            chr.setOrganism(getOrganism());
            chr.addDataSets(getDataSet());
            chr.setSequenceOntologyTerm(getSoTerm("chromosome"));
            getDirectDataLoader().store(chr);
            imoTracker.remove(chr.getId());
            createdChromosomeMap.put(identifier, chr);
        }
        return chr;
    }

    /** For a given identifier + source, returns an AliasName entity
     * @param identifier
     * @param source
     * @return
     * @throws ObjectStoreException
     */
    private AliasName getAliasName(String identifier, String source) throws ObjectStoreException {
        AliasName alias;
        if (createdAliasNameMap.containsKey(identifier)) {
            alias = createdAliasNameMap.get(identifier);
        } else {
            alias = getDirectDataLoader().createObject(AliasName.class);
            imoTracker.put(alias.getId(), alias);
            alias.setIdentifier(identifier);
            alias.setSource(source);
            alias.setOrganism(getOrganism());
            alias.addDataSets(getDataSet());
            getDirectDataLoader().store(alias);
            imoTracker.remove(alias.getId());
            createdAliasNameMap.put(identifier, alias);
        }
        return alias;
    }

    /**
     * Returns a Ontology entity for 'Sequence Ontology'
     * @return
     * @throws ObjectStoreException
     */
    private Ontology getSequenceOntology() throws ObjectStoreException {
        if (ontology == null) {
            ontology = getDirectDataLoader().createObject(Ontology.class);
            imoTracker.put(ontology.getId(), ontology);
            ontology.setName("Sequence Ontology");
            ontology.setUrl("http://www.sequenceontology.org");
            ontology.addDataSets(getDataSet());
            getDirectDataLoader().store(ontology);
            imoTracker.remove(ontology.getId());
        }
        return ontology;
    }

    /**
     * Returns a SOTerm entity for a given SO feature type
     * @param featureType
     * @return
     * @throws ObjectStoreException
     */
    private SOTerm getSoTerm(String featureType) throws ObjectStoreException {
        SOTerm soTerm = createdSotermMap.get(featureType);
        if (soTerm == null) {
            soTerm = getDirectDataLoader().createObject(SOTerm.class);
            imoTracker.put(soTerm.getId(), soTerm);
            soTerm.setOntology(getSequenceOntology());
            soTerm.setName(featureType);
            soTerm.addDataSets(getDataSet());
            getDirectDataLoader().store(soTerm);
            imoTracker.remove(soTerm.getId());
            createdSotermMap.put(featureType, soTerm);
        }
        return soTerm;
    }

    /**
     * Convenience method for printing a list of string
     * @param line
     */
    private void printLine(String[] line) {
        for (int i = 0; i < line.length; i++) {
            System.out.print(line[i] + "\t");
        }
        System.out.print("\n");
    }
}
