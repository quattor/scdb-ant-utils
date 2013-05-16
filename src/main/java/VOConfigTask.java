/*
${license-info}
${developer-info}
${author-info}
*/

package org.quattor.ant;

import java.io.*;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.net.URL;
import java.net.MalformedURLException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.DirSet;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;


/**
 * This task creates pan templates describing VO parameters from VO ID cards stored on CIC portal
 * 
 * @author Michel Jouvin (based on C. Duprilot's implementation)
 */

public class VOConfigTask extends Task {

    // Variables

    /*
     *  scdb-ant-utils version
     */
    private final String version = "${version}";

    /* List of directories containing a VO configuration description (templates) */
    protected DirSet configDirs = null;

    /* Namespace (relative directory) to use for templates containing VO parameters */
    protected String paramsTplNS = null;

    /* Namespace (relative directory) to use for templates containing VOMS server certificates */
    protected String certsTplNS = null;

    /* Namespace (relative directory) to use for templates containing VO site-specific params */
    protected String siteParamsTplNS = null;

    /* URI for VO ID card source */
    protected String voIdCardsUri = null;

    /* Name of the template containing the list of all defined VOs */
    protected String allVosTemplate = "allvos";

    /* Name of the template containing the list of all defined VOs */
    protected String vomsServerDNsTemplate = "voms_dn_list";

    /* Algorithm used to generate account suffix.
     * The original one was very bad at ensuring suffix uniqueness, requiring several retries to get
     * a unique suffix and thus making the actual suffix dependent on the FQAN order which historicall was alphabetical.
     * With the new algorithm, there is a very small chance of suffix conflict and the FQAN order in the VO card is
     * maintained to limit side effect of generation retries (with the assumption that new FQAN are added at the end of
     * the list in the VO ID card.
     * Default is to use the new algorithm but the option must be passed explicitly to preserve account
     * backward compatibility (in term of account names and uids).
     */
    protected boolean legacySuffixAlgorithm = false;

    /* Control printing of debugging messages in this task */
    protected boolean debugTask = false;

    /* Configuration of VOs retrieved from VO ID cards.
     * This is a hash with one entry per VO : the key is the VO name.
     */
    protected TreeMap<String,VOConfig> voTable = new TreeMap<String,VOConfig>();;

    /* Hash table of all defined VOMS servers.
     * Used to check consistency of VOMS server attributes across VOs.
     */
    protected TreeMap<String,VOMSServer>vomsServers = new TreeMap<String,VOMSServer>();;
    
    /* HashSet of generated VO account prefix: used to detect potential clashes */
    // accountPrefixes keeps track of the first VO to use a prefix
    protected Hashtable<String,String> accountPrefixes = new Hashtable<String,String>();
    // accountPrefixConflicts keeps track of VOs using a conflicting prefix
    protected Hashtable<String,AccountPrefixConflict> accountPrefixConflicts = new Hashtable<String,AccountPrefixConflict>();


    // Methods

    /**
     * Setting this flag will print debugging information from the task itself.
     * This is primarily useful if one wants to debug this task. Output can be
     * very verbose...
     * 
     * @param debugTask
     *            flag to print task debugging information
     */
    public void setDebugTask(boolean debugTask) {
        this.debugTask = debugTask;
    }

    /**
     * Support nested dirset elements. This is called by ant only after all of
     * the children of the dirset have been processed. Collect all of the
     * selected directories from the dirset.
     * 
     * @param dirset
     *            a configured DirSet
     */
    public void addConfiguredDirSet(DirSet configDirs) {
        this.configDirs = configDirs;
    }

    /**
     * Set the url to use to download VO ID cards (XML file).
     * 
     * @param voIdCardsUrl
     *            String containing the template form of the path of the url
     * 
     */
    public void setvoIdCardsUri(String voIdCardsUri) {
        this.voIdCardsUri = voIdCardsUri;
    }

    /**
     * Set the namespace (relative directory) for the generated VO templates.
     * 
     * @param paramsTplNS
     *            String containing full path of the directory
     * 
     */
    public void setparamsTplNS(String paramsTplNS) {
        this.paramsTplNS = paramsTplNS;
    }

    /**
     * Set the namespace (relative directory) for the generated templates containing certificates.
     * 
     * @param certsTplNS
     *            String containing the template form of the path of the
     *            directory
     * 
     */
    public void setcertsTplNS(String certsTplNS) {
        this.certsTplNS = certsTplNS;
    }

    /**
     * Set the namespace (relative directory) for site-specific templates related to VO cnfiguration.
     * 
     * @param siteParamsTplNS
     *            String containing a template form of the path to the
     *            customization directory
     * 
     */
    public void setsiteParamsTplNS(String siteParamsTplNS) {
        this.siteParamsTplNS = siteParamsTplNS;
    }

    /**
     * Set the version of the algorithm to use for generating account suffix.
     * 
     * @param legacySuffixAlgorithm
     *            if true, used the legacy algorithm for backward compatibility.
     * 
     */
    public void setlegacySuffixAlgorithm(boolean legacySuffixAlgorithm) {
        this.legacySuffixAlgorithm = legacySuffixAlgorithm;
    }


    // Possible FQAN for a Software Manager
    static final private String softwareManagerSuffix = "s";
    static private HashSet<String> fqanSWManager;
    static {
        fqanSWManager = new HashSet<String>();
        fqanSWManager.add("/Role=lcgadmin");
        fqanSWManager.add("/admin");
        fqanSWManager.add("/Role=swadmin");
        fqanSWManager.add("/Role=sgmadmin");
        fqanSWManager.add("/Role=sgm");
        fqanSWManager.add("/Role=SoftwareManager");
        fqanSWManager.add("/Role=VO-Software-Manager");
        fqanSWManager.add("/Role=SW-Admin");
    }
    
    // Possible FQAN for production role (required for backward compatibility)
    static final private String productionManagerSuffix = "p";
    static private HashSet<String> fqanProductionManager;
    static {
        fqanProductionManager = new HashSet<String>();
        fqanProductionManager.add("/Role=production");
        fqanProductionManager.add("/Role=Production");
        fqanProductionManager.add("/Role=prod");
        fqanProductionManager.add("/Role=ProductionManager");
    }
    
    // Possible FQAN for pilot role (required for backward compatibility)
    static final private String pilotSuffix = "pilot";
    static private HashSet<String> fqanPilot;
    static {
        fqanPilot = new HashSet<String>();
        fqanPilot.add("/Role=pilot");
    }
    

    /*
     * Class method to generate a string representing a number in a base26-like representation.
     * Base26 digits are normally from 0 (zero) to P but the returned string is containing only
     * letters: digits from 0 to 9 are replaced by letters from q to z.
     * The returned string is in lowercase.
     */
    public static String toBase26(int i) {
        // i must be converted to an unsigned long, else the string will contain the '-' sign
	long unsignedI = i & 0xffffffffL;
        StringBuilder string26 = new StringBuilder(Long.toString(unsignedI,26));
        final int numberOffset = 'P' - '0' + 1;

        for (int k = 0; k < string26.length(); k++) {
            if ( (string26.charAt(k) >= '0') && (string26.charAt(k) <= '9') )  {
                string26.setCharAt(k, (char) (string26.charAt(k)+numberOffset));
            }
        }
        return (string26.toString().toLowerCase());
    }

    
    /*
     * Method used by ant to execute this task.
     */

    @Override
    public void execute() throws BuildException {

        // Parse VO ID cards
        SAXParserFactory factory = SAXParserFactory.newInstance();
        try {
            URL voIdCardsUrl = new URL(voIdCardsUri);
            InputStream urlstream = voIdCardsUrl.openStream();
            SAXParser parser = factory.newSAXParser();
            parser.parse(urlstream, new VOCardHandler());        
        } catch (MalformedURLException e) { 
            System.out.println("Invalid format used for specifying the source of VO ID cards (voIdCardsUri): "+voIdCardsUri);
            throw new BuildException("BUILD FAILED : " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Failed to open VO ID card source ("+voIdCardsUri+")");
            throw new BuildException("BUILD FAILED : " + e.getMessage());            
        } catch (Exception e) {
            System.out.println("Error parsing VO ID cars ("+voIdCardsUri+")");
            throw new BuildException("BUILD FAILED : " + e.getMessage());                        
        }

        // Retrieve list of template branches to update
        DirectoryScanner ds = configDirs.getDirectoryScanner(getProject());
        File templateBasedir = ds.getBasedir();
        
        // Write templates
        Set<Entry<String,VOConfig>> voTableEntries = voTable.entrySet();
        Set<Entry<String,VOMSServer>> vomsServersEntries = vomsServers.entrySet();
        for (String branch : ds.getIncludedDirectories()) {
            System.out.println("Updating templates in branch "+branch);
            String templateBranch = templateBasedir+"/"+branch;
            // Write VO configurations to templates
            for (Entry<String,VOConfig> vo : voTableEntries) {
                vo.getValue().writeVOTemplate(templateBranch);
            }
            // Write VOMS server certificate templates
            for (Entry<String,VOMSServer> server : vomsServersEntries) {
                server.getValue().updateVOMSServerTemplate(templateBranch);
            }
            // Write template containing the list all defined VOs
            writeVOList(templateBranch);
            // Write subject name and issuer of all valid VOMS server certificates
            // in template used to produce .lsc files
            writeDNList(templateBranch);
            
        }
        
        // Warn about VO account prefix conflicts if any
        if ( accountPrefixConflicts.size() > 0 ) {
            System.out.println("\nWARNING: the following account prefixes are used by several VOs:");
            Set<Entry<String,AccountPrefixConflict>> conflictEntries = accountPrefixConflicts.entrySet();
            for (Entry<String,AccountPrefixConflict> entry : conflictEntries) {
                System.out.println("    "+entry.getKey()+": "+entry.getValue());
            }
            System.out.println("Should you use several conflicting VOs, be sure to define their account prefix explicitly.");
        }
        
        
    }

    /*
     *  Write template containing a list of all defined VOs
     */
    protected void writeVOList(String templateBranch) {
        String voListNS = paramsTplNS + "/" + allVosTemplate;
        String voListTpl = templateBranch + "/" + voListNS + ".tpl";
        System.out.println("Writing the list of defined VOs ("+voListNS+")");

        
        try {
            FileWriter template = new FileWriter(voListTpl);
            template.write("unique template "+voListNS+";\n\n");
            template.write("variable ALLVOS ?= list(\n");
            for (String vo : voTable.keySet()) {
                template.write("        '"+vo+"',\n");
            }
            template.write(");\n\n");
            template.close();
        } catch (IOException e){
            throw new BuildException("Error writing the VO list ("+voListTpl+")\n"+e.getMessage());
        }            
    }
    

    /*
     *  Write template containing subject/issuer of all valid VOMS server certificates
     */
    protected void writeDNList(String templateBranch) {
        String dnListNS = certsTplNS + "/" + vomsServerDNsTemplate;
        String dnListTpl = templateBranch + "/" + dnListNS + ".tpl";
        System.out.println("Writing the list of defined VOs ("+dnListNS+")");

        
        try {
            FileWriter template = new FileWriter(dnListTpl);
            template.write("unique template "+dnListNS+";\n\n");
            if ( vomsServers != null ) {
                template.write("variable VOMS_SERVER_DN ?= list(\n");
                TreeSet<String> serverList = new TreeSet<String>();
                for (String server : vomsServers.keySet()) {
                    serverList.add(server);
                }
                for (String server : serverList) {
                    vomsServers.get(server).writeCertInfo(template);
                }
                template.write(");\n\n");
            } else {
                if ( debugTask ) {
                    System.err.println("    No VOMS server defined in any VO");
                }
            }
            template.close();
        } catch (IOException e){
            throw new BuildException("Error writing the VO list ("+dnListTpl+")\n"+e.getMessage());
        }            
    }
    

    // SAX content handler for VO cards

    public class VOCardHandler extends DefaultHandler {

        /* Configuration of VO currently being processed */
        protected VOConfig voConfig = null;

        /* Context variables */
        protected boolean sectionVOMSServers = false;
        protected boolean sectionGroupsRoles = false;
        protected VOMSServer vomsServer = null;
        protected VOMSEndpoint vomsEndpoint = null;
        protected VOMSFqan fqan = null;
        protected String data = null;

        /**
         * Start of new element
         */

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if ( qName.equals("IDCard") ) {
                String voName = attributes.getValue("Name");
                if ( voName == null ) {
                    throw new SAXException("Invalid configuration: VO has no name");
                }
                voName = voName.toLowerCase();
                System.out.println("Retrieving configuration for VO "+voName);
                voConfig = new VOConfig();
                String voId = attributes.getValue("CIC_ID");
                if ( voId == null ) {
                    throw new SAXException("Invalid configuration: VO has no Id");
                }
                voConfig.setName(voName);
                voConfig.setId(Integer.parseInt(voId));
                
            } else if ( qName.equals("VOMSServers") ) {
                sectionVOMSServers = true;
                
            } else if ( qName.equals("FQANs") ) {
                sectionGroupsRoles = true;
                
            } else if ( sectionVOMSServers ) {
                if ( qName.equals("VOMS_Server") ) {
                    vomsServer = new VOMSServer();        
                    vomsEndpoint = new VOMSEndpoint();
                    String httpsPort = attributes.getValue("HttpsPort");
                    if ( httpsPort == null ) {
                        throw new SAXException("Invalid configuration: VOMS Server has no https port");
                    }
                    vomsServer.setPort(httpsPort);
                    String vomsesPort = attributes.getValue("VomsesPort");
                    if ( vomsesPort == null ) {
                        throw new SAXException("Invalid configuration: VOMS Endpoint has no vomses port");
                    }
                    vomsEndpoint.setPort(vomsesPort);
                    String vomsAdminEnabled = attributes.getValue("IsVomsAdminServer");
                    if (vomsAdminEnabled == null) {
                        throw new SAXException("Invalid configuration: IsVomsAdminServer is not defined");
                    }
                    vomsEndpoint.setVomsAdminEnabled(vomsAdminEnabled);
                } else {
                    // This will enable collection/concatenation of data in characters()
                    data = "";
                }
                
            } else if ( sectionGroupsRoles ) {
                if ( qName.equals("FQAN") ) {
                    fqan = new VOMSFqan();
                    String isGroupUsed = attributes.getValue("IsGroupUsed");
                    if (isGroupUsed != null) {
                    	fqan.setMappingRequested(isGroupUsed);
                    }
                    String groupType = attributes.getValue("GroupType");
                    if (groupType != null) {
                    	fqan.setReservedRoles(groupType);
                    }
                } else {
                    // This will enable collection/concatenation of data in characters()
                    data = "";
                }
                
            }
        }

        /**
         * End of an element
         */

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if ( qName.equals("IDCard") ) {
                if ( voConfig.getName() != null ) {
                    try {
                        voTable.put(voConfig.getName(), voConfig);
                    } catch (NullPointerException e) {
                        throw new SAXException("Internal error: voTable or voConfig undefined at the end of VO "+voConfig.getName()+" configuration");
                    }
                    if ( debugTask ) {
                        System.out.println("Finished processing VO "+voConfig.getName());                        
                    }
                } else {
                    throw new SAXException("Parsing error: end of VO configuration found before start");
                }

            } else if ( qName.equals("VOMSServers") ) {
                sectionVOMSServers = false;

            } else if ( qName.equals("FQANs") ) {
                sectionGroupsRoles = false;
                
            } else if ( sectionVOMSServers ) {
                // Check the VOMS server has not been defined yet by another VO or that attributes are consistent
                // A unique VOMS server is identified by host+port combination
                String VOMSServerKey = vomsServer.getHost() + ":" + Integer.toString(vomsServer.getPort());
                if ( qName.equals("VOMS_Server") ) {
                    // Check the VOMS server for important attributes
                    if ( vomsServer.getHost() == "" ) {
                        System.err.println("    WARNING: a VOMS server has no hostname defined");
                    } else if ( vomsServer.getPort() == 0 ) {
                        System.err.println("    WARNING: a VOMS server has no port defined.");
                    } else {
                        if ( vomsServers.containsKey(VOMSServerKey) ) {
                            if ( debugTask ) {
                                System.err.println("    VOMS server '"+VOMSServerKey+"' already defined: checking attribute consistency.");
                            }
                            if ( (vomsServer.getCertExpiry() != null) && (vomsServer.getCert() != vomsServers.get(VOMSServerKey).getCert()) ) {
                                if ( vomsServers.get(VOMSServerKey).getCert().length() == 0 ) {
                                    System.err.println("    WARNING: VOMS server '"+VOMSServerKey+"' already defined but without certificate, updating it.");
                                    vomsServers.get(VOMSServerKey).setCert(vomsServer.getCert());
                                } else {
                                    if ( vomsServer.getCertExpiry().after(vomsServers.get(VOMSServerKey).getCertExpiry())) {
                                        System.err.println("    WARNING: VOMS server '"+VOMSServerKey+"' already defined with an older certificate, updating it.");
                                        vomsServers.get(VOMSServerKey).setCert(vomsServer.getCert());
                                    } else if ( vomsServer.getCertExpiry().before(vomsServers.get(VOMSServerKey).getCertExpiry())) {
                                        System.err.println("    WARNING: VOMS server '"+VOMSServerKey+"' already defined with a newer certificate, keeping previous one.");
                                    }
                                }
                            }
                        } else {
                            if ( debugTask ) {
                                System.err.println("    Adding VOMS server '"+VOMSServerKey+"' to global VOMS server list.");
                            }
                            vomsServers.put(VOMSServerKey,vomsServer);
                        }
                        vomsEndpoint.setServer(vomsServers.get(VOMSServerKey));
                        //vomsEndpoint.setEndpoint("vomss://" + vomsServer.getHost() + ":" + Integer.toString(vomsServer.getPort()) + "/voms/" + voConfig.getName() + "?/" + voConfig.getName());
                        voConfig.addVomsEndpoint(vomsEndpoint);
                    }
                } else {
                    if ( qName.equals("hostname") ) {
                        String hostname = data.trim();
                        vomsServer.setHost(data.trim());
                    } else if ( qName.equals("X509PublicKey") ) {
                        vomsServer.setCert(data);
                    }
                    // Disable collection of data
                    data = null;
                }

            } else if ( sectionGroupsRoles ) {
                if ( qName.equals("FQAN") ) {
                    fqan.setReservedRoles(voConfig);
                    // An empty value for the FQAN means this FQAN must not be added to the list.
                    if ( fqan.getFqan().length() != 0 ) {
                        if ( fqan.isPilotRole() ) {
                            voConfig.setPilotRoleFQAN(fqan.getFqan());
                        }
                        voConfig.fqanList.put(fqan.getFqan(),fqan);
                    }
                } else {
                    if ( qName.equals("FqanExpr") ) {
                        fqan.setFqan(data.trim(),voConfig.getName());
                    } else if ( qName.equals("Description") ) {
                        fqan.setDescription(data.trim());
                    }
                    // Disable collection of data
                    data = null;
                }
                
            }            
        }

        /*
         * Retrieve element data: data can be splitted into several chunks that must be concatenated to get the actual data
         */
        
        @Override
        public void characters (char[] chars, int start, int length) throws SAXException {
            if ( data != null ) {
                String chunk = new String(chars,start,length);
                data += chunk;
            }
        }

    }
        

    // Class representing a VO

    private class VOConfig {
        /* VO name */
        protected String name = null;
        
        /* VO ID number */
        protected int id = 0;
        
        /* Account prefix */
        protected String accountPrefix = null;

        /* List of VOMS servers */
        protected LinkedList<VOMSEndpoint> vomsEndpointList = new LinkedList<VOMSEndpoint>();

        /* List of defined FQANs */
        protected LinkedHashMap<String,VOMSFqan> fqanList = new LinkedHashMap<String,VOMSFqan>();
        
        /* FQAN corresponding to pilot role */
        protected String pilotRoleFQAN = null;
        
        /* HashSet of generated account suffix: used to enforce uniqueness */
        protected HashSet<String> accountSuffixes = new HashSet<String>();


        // Methods

        public void addVomsEndpoint(VOMSEndpoint vomsEndpoint) {
            vomsEndpointList.add(vomsEndpoint);
        }

        public void addAccountSuffix(String suffix) {
            accountSuffixes.add(suffix);
        }

        public boolean accountSuffixUnique(String suffix) {
            return (!accountSuffixes.contains(suffix));
        }
        
        public String getAccountPrefix() {
            if ( this.accountPrefix == null ) {
                setAccountPrefix();
            }
            return (this.accountPrefix);
        }
        
        public int getBaseUid() throws BuildException {
            if ( getId() == 0 ) {
                throw new BuildException("VO "+getName()+": VO ID undefined, base_uid cannot be computed");
            }
            return (getId() * 1000);
        }

        public int getId() {
            return (this.id);
        }

        public String getName() {
            return (this.name);
        }
        
        public String getPilotRoleFQAN() {
            return (this.pilotRoleFQAN);
        }
        
        public LinkedList<VOMSEndpoint> getVomsEndpointList() {
            return (this.vomsEndpointList);
        }

        /*
         * Method to generate account prefix for the VO.
         * The account prefix is made of the first 3 letters of the VO name (after removal of all non alphanumeric
         * characters and the 'vo.' prefix if any) followed by letters generated from base26-like conversion of
         * the VO id.
         * In case of clash between 2 VOs, no attempt is made to solve it but a warning is displayed that if using
         * several of the conflicting VOS, some VO prefix must be defined explicitly.
         */
        public void setAccountPrefix() throws BuildException {
            if ( (getName() == null) || (getId() == 0) ) {
                throw new BuildException("VO name or ID undefined: cannot generate account prefix");                
            }
            this.accountPrefix = getName().replaceFirst("^vo\\.", "").replaceAll("[^A-Za-z0-9]", "").substring(0,3);
            this.accountPrefix += VOConfigTask.toBase26(getId());
            // Check uniqueness and keep track of potential conflicts
            if ( accountPrefixes.containsKey(this.accountPrefix) ) {
                if ( debugTask ) {
                    System.err.println("    VO "+getName()+": generated account prefix ("+this.accountPrefix+") already used by another VO");
                }
                AccountPrefixConflict conflicts;
                if ( accountPrefixConflicts.containsKey(this.accountPrefix) ) {
                    conflicts = accountPrefixConflicts.get(this.accountPrefix);
                } else {
                    conflicts = new AccountPrefixConflict();
                    conflicts.addVO(accountPrefixes.get(this.accountPrefix));
                    accountPrefixConflicts.put(this.accountPrefix, conflicts);
                }
                conflicts.addVO(getName());
            } else {
                accountPrefixes.put(this.accountPrefix,getName());
            }
        }
        
        public void setId(int id) {
            this.id = id;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setPilotRoleFQAN(String fqan) {
            this.pilotRoleFQAN = fqan;
        }

        public String toString() {
            String configStr = "";
            for (VOMSEndpoint endpoint : vomsEndpointList) {
                if ( configStr.length() > 0 ) {
                    configStr += "\n";
                }
                configStr += "    VOMS Server: "+endpoint.getEndpoint()+" (VOMS port="+endpoint.getPort()+
                                                                ", voms-admin="+endpoint.getVomsAdminEnabled()+")";
            }                
            return (configStr);
        }
        
        private void writeVOTemplate(String templateBranch) throws BuildException {            
            if ( debugTask ) {
                System.err.println("VO configuration for VO "+getName()+" (ID="+getId()+"):\n"+this);
            }
            
            String voParamsNS = paramsTplNS + "/" + getName();
            String voParamsTpl = templateBranch + "/" + voParamsNS + ".tpl";
            System.out.println("Writing template for VO "+getName()+" ("+voParamsTpl+")");

            try {
                FileWriter template = new FileWriter(voParamsTpl);
                template.write("structure template "+voParamsNS+";\n");
                template.write("\n");
                template.write("'name' ?= '"+getName()+"';\n");
                template.write("'account_prefix' ?= '"+getAccountPrefix()+"';\n");
                template.write("\n");
                template.write("'voms_servers' ?= list(\n");
                boolean forceVomsAdmin = false;
                if ( getVomsEndpointList().isEmpty() ) {
                    System.err.println("    WARNING: VO "+getName()+" has no VOMS endpoint defined");
                } else if ( getVomsEndpointList().size() == 1 ) {
                    forceVomsAdmin = true;
                }
                for (VOMSEndpoint vomsEndpoint : getVomsEndpointList()) {
                    vomsEndpoint.writeTemplate(template,forceVomsAdmin);
                }
                template.write(");\n");
                template.write("\n");
                template.write("'voms_mappings' ?= list(\n");
                if ( debugTask && fqanList.isEmpty() ) {
                    System.err.println("    INFO: VO "+getName()+" has no specific FQAN defined");
                }
                // Pilot role if defined must be written first to ensure it uses the first available UID
                // after reserved UIDs.
                if ( getPilotRoleFQAN() != null ) {
                    fqanList.get(getPilotRoleFQAN()).writeTemplate(template,this);
                }
                for (String key : fqanList.keySet()) {
                    VOMSFqan fqan = fqanList.get(key);
                    if ( !fqan.isPilotRole() ) {
                        fqan.writeTemplate(template,this);
                    }
                }
                template.write(");\n");
                template.write("\n");
                template.write("'base_uid' ?= "+getBaseUid()+";\n");
                template.close();
            } catch (IOException e){
                throw new BuildException("Error writing template for VO "+getName()+" ("+voParamsTpl+")\n"+e.getMessage());
            }            
        }

    }


    // Class representing a VOMS server endpoint (used by a specific V0)
    
    private class VOMSEndpoint {
        protected VOMSServer server = null;
        protected int port;
        protected String endpoint = null;
        protected boolean vomsAdminEnabled = true;
        
        // Methods
        
        public VOMSServer getServer() {
            return (this.server);
        }
        
        public int getPort() {
            return (this.port);
        }
        
        public String getEndpoint() {
            return (this.endpoint);
        }
        
        public boolean getVomsAdminEnabled() {
            return (this.vomsAdminEnabled);
        }

        public void setServer(VOMSServer server) {
            this.server = server;
        }
        
        public void setPort (String port) {
            this.port = Integer.parseInt(port);
        }
        
        public void setEndpoint (String endpoint) {
            this.endpoint = endpoint;
        }

        public void setVomsAdminEnabled(String vomsAdminEnabled) {
            if ( (vomsAdminEnabled != null) && !vomsAdminEnabled.contentEquals("0") ) {
                vomsAdminEnabled = "true";
            }
            this.vomsAdminEnabled = Boolean.parseBoolean(vomsAdminEnabled);
        }

        public void writeTemplate(FileWriter template, boolean forceVomsAdmin) throws IOException {
            template.write("    nlist('name', '"+getServer().getHost()+"',\n");
            template.write("          'host', '"+getServer().getHost()+"',\n");
            template.write("          'port', "+getPort()+",\n");
            template.write("          'adminport', "+getServer().getPort()+",\n");
            if ( !getVomsAdminEnabled() ) {
                if ( !forceVomsAdmin ) {
                    template.write("          'type', list('voms-only'),\n");
                } else {
                    System.out.println("    WARNING: voms-admin enabled on "+getServer().getHost()+" as this is the only VOMS server (VO card inconsistency)");
                }
            }
            template.write("         ),\n");
        }
    }
    
    
    // Class representing a VOMS server

    private class VOMSServer {
        protected String host = null;
        protected int port = 8443;
        protected VOMSServerCertificate cert = null;
        protected VOMSServerCertificate oldCert = null;
        protected boolean oldCertRetrieved = false;
        protected Pattern certDeclarationPattern = Pattern.compile("\\s*('|\")(old)?cert\\1\\s*\\??=\\s*\\{*\\s*<<(\\w+)\\s*\\}*\\s*;(?:\\n|\\r)+");
        
        
        // Methods

        public String getCert() {
            if ( this.cert == null ) {
                return ("");
            } else {
                return (this.cert.getCert());                
            }
        }
        
        public Date getCertExpiry () {
            if ( this.cert == null ) {
                return(null);
            } else {
                return (this.cert.getExpiry());
            }
        }
        
        protected String getCertParamsNS() {
            return (certsTplNS + "/" + getHost());            
        }

        protected String getCertParamsTpl(String templateBranch) {
            return (templateBranch + "/" + getCertParamsNS() + ".tpl");
        }

        public String getHost() {
            return (this.host);
        }
        
        protected String getOldCert(String templateBranch) {
            if ( !oldCertRetrieved ) {
                setOldCert(templateBranch);
                oldCertRetrieved = true;
            }
            if ( this.oldCert == null ) {
                return ("");
            } else {
                return (this.oldCert.getCert());  
            }
        }
        
        public int getPort() {
            return (this.port);
        }

        public void setCert(String cert) {
            try {
                this.cert = new VOMSServerCertificate(cert);
            } catch (CertificateException e) {
                this.cert = null;
            }
        }

        public void setHost(String host) {
            this.host = host;
        }

        public void setPort (String port) {
            this.port = Integer.parseInt(port);
        }

        /*
         *  If a previous version of the template exists, retrieve the certificates defined ('cert' 
         *  and 'oldcert') and define 'oldCert' to the certificate not matching the one in VO ID card.
         *  If not existing certificate can be retrieved, return an empty string.
         */
        protected void setOldCert(String templateBranch) throws BuildException {
            if ( this.cert == null ) {
                if ( debugTask ) {
                    System.err.println("    VOMS server "+getHost()+" has no certificate defined in VO ID card. Ignoring existing certificate.");
                }
                return;
            }
                        
            String certParamsTpl = getCertParamsTpl(templateBranch);
            File templateFile = new File(certParamsTpl);
            Hashtable<String,VOMSServerCertificate> existingCerts = new Hashtable<String,VOMSServerCertificate>();

            if ( templateFile.exists() ) {
                if ( debugTask ) {
                    System.err.println("    Retrieving VOMS server "+getHost()+" existing certificate");
                }
                try {
                    Scanner templateScanner = new Scanner(templateFile);
                    String certStartTag;
                    boolean certFound = false;
                    while ( (certStartTag = templateScanner.findWithinHorizon(certDeclarationPattern,0)) != null ) {
                        certFound = true;
                        Matcher delimiterMatcher = certDeclarationPattern.matcher(certStartTag);
                        if ( delimiterMatcher.matches() ) {
                            String certType = "cert";
                            if ( delimiterMatcher.group(2) != null ) {
                                certType = "oldcert";                                
                            }
                            String delimiter = delimiterMatcher.group(3);
                            //if ( debugTask ) {
                            //    System.err.println("Certificate delimiter="+delimiter);
                            //}
                            templateScanner.useDelimiter(delimiter+"\\s*;*");
                            String certValue = templateScanner.next();
                            if ( certValue != null ) {
                                try {
                                    existingCerts.put(certType, new VOMSServerCertificate(certValue));
                                } catch (CertificateException e) {
                                        System.out.println("    Existing certificate ('"+certType+"') no longer valid, ignoring it.");
                                }
                            } else {
                                System.out.println("    WARNING: invalid format of certificate declaration ('"+certType+"') in existing template");
                            }
                        } else {
                            if ( debugTask ) {
                                System.err.println("    WARNING: failed to match certificate delimiter in existing template");
                            }
                        }
                    }
                    if ( !certFound ) {
                        if ( debugTask ) {
                            System.err.println("    Failed to match '"+certDeclarationPattern+"' in existing template");
                        };
                    } else if ( existingCerts.size() > 0 ) {
                        // If 'cert' was defined and is not matching the cert in VO ID card, use it for 'oldcert'
                        if ( existingCerts.containsKey("cert") && !existingCerts.get("cert").equals(this.cert) ) {
                            if ( debugTask ) {
                                System.err.println("    Existing certificate ('cert') found and different from VO ID card: 'oldcert' defined");
                            }
                            this.oldCert = existingCerts.get("cert");
                        } else if ( existingCerts.containsKey("oldcert") && !existingCerts.get("oldcert").equals(this.cert) ) {
                            if ( debugTask ) {
                                System.err.println("    Existing certificate ('oldcert') found and different from VO ID card: 'oldcert' defined");
                            }                            
                            this.oldCert = existingCerts.get("oldcert");
                        } else {
                            if ( debugTask ) {
                                System.err.println("    Existing certificates match the new certificate: 'oldcert' not defined");
                            }
                        }
                    }
                } catch (FileNotFoundException e) {
                    // Should not happen as file existence was tested just before
                    throw new BuildException("Internal error: file not found in VOMSServer.updateVOMSServerTemplate()");
                } catch (NoSuchElementException e) {
                    if ( debugTask ) {
                        System.err.println("    Failed to retrieve current certificate in exiting template ");
                    }
                }
            } else {
                if ( debugTask ) {
                    System.err.println("    No certificate previously defined for VOMS server "+getHost()+": 'oldcert' not defined");
                }
            }
        }

        public void updateVOMSServerTemplate(String templateBranch) throws BuildException {
            String certParamsTpl = getCertParamsTpl(templateBranch);
            System.out.println("Updating template for VOMS server "+getHost()+" ("+certParamsTpl+")");
            
            // Existing certificate must be retrieved before creating new template
            String oldCert = getOldCert(templateBranch);

            try {
                FileWriter template = new FileWriter(certParamsTpl);
                template.write("structure template "+getCertParamsNS()+";\n\n");
                template.write("'cert' ?= <<EOF;\n");
                template.write(getCert());
                template.write("EOF\n\n");
                // Zero length means no certificate
                if ( oldCert.length() > 0 ) {
                    template.write("'oldcert' ?= <<EOF;\n");
                    template.write(oldCert);
                    template.write("EOF\n\n");
                }
                template.close();
            } catch (IOException e){
                throw new BuildException("Error writing template for VOMS server "+getHost()+" ("+certParamsTpl+")\n"+e.getMessage());
            }            
        }
        
        /*
         * This method writes subject and issuer of VOMS server valid certificates as
         * a nlist element
         */
        public void writeCertInfo(FileWriter template) throws IOException {
            LinkedList<VOMSServerCertificate> certs = new LinkedList<VOMSServerCertificate>();
            if ( this.cert != null ) {
                certs.add(cert);
            }
            if ( this.oldCert != null ) {
                certs.add(oldCert);
            }
            String entrySuffix = "";
            String subject = null;
            String issuer = null;
            for (VOMSServerCertificate cert: certs) {
                boolean writeEntry = false;
                if ( (subject == null) || !subject.equals(cert.getDN()) ) {
if ( (entrySuffix.length() > 0) ) {
  System.out.println("New cert subject found: old="+subject+", new="+cert.getDN());
}
                    subject = cert.getDN();
                    writeEntry = true;
                }
                if ( (issuer == null) || !issuer.equals(cert.getIssuer()) ) {
if ( (entrySuffix.length() > 0) ) {
  System.out.println("New cert issuer found: old="+issuer+", new="+cert.getIssuer());
}
                    issuer = cert.getIssuer();
                    writeEntry = true;
                }
                if ( writeEntry ) {
                    template.write(String.format("%-36s%s\n", "    '"+getHost()+entrySuffix+"', ", "nlist('subject', '"+subject+"',"));
                    template.write(String.format("%-42s%s\n","", "'issuer', '"+issuer+"',"));
                    template.write(String.format("%-41s%s\n","", "),"));
                    entrySuffix = "_2";                    
                }
            }
        }
    }
    
    
    // Class to represent a VOMS server certificate
    
    private class VOMSServerCertificate {
        // An empty string for base64 means that the certificate is not valid and must be ignored
        private String base64 = null;
        private BigInteger serial = null;
        private String dn = null;
        private String issuer= null;
        private Date expiry = null;
        
        // Constructor: retrieve main informations from certificate
        public VOMSServerCertificate (String base64) throws CertificateException {
            this.base64 = base64;
            try {
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                X509Certificate cert = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(base64.getBytes()));
                cert.checkValidity();
                this.expiry = cert.getNotAfter();
                this.serial = cert.getSerialNumber();
                this.dn = ldapDN(cert.getSubjectDN().getName());
                this.issuer = ldapDN(cert.getIssuerDN().getName());
            } catch (CertificateExpiredException e) {
                System.out.println("    VOMS server certificate expired: ignoring it");
                throw e;
            } catch (CertificateException e) {
                System.out.println("    Invalid VOMS server certificate: "+e.getMessage());
                throw e;
            }
        }
        
        public boolean equals (VOMSServerCertificate cert) {
            if ( getSerial().equals(cert.getSerial()) ) {
                return (true);
            } else {
                return (false);
            }
        }
        
        public String getCert() {
            return (this.base64);
        }
        
        public String getDN() {
            return (this.dn);
        }
        
        public Date getExpiry() {
            return (this.expiry);
        }
        
        public String getIssuer() {
            return (this.issuer);
        }
        
        public BigInteger getSerial() {
            return (this.serial);
        }
        
        // Convert the java standard representation of a DN to LDAP one.
        // Revert order of attribues, '/' instead of ',' as a separator
        protected String ldapDN (String dn) {
            String[] tokens = dn.split(",\\s*");
            String ldapDN = "";
            for (int i=Array.getLength(tokens)-1; i>=0; i--) {
                ldapDN += "/" + tokens[i];
            }
            return (ldapDN);
        }
}
    
    
    // VOMS FQAN
    
    private class VOMSFqan {
        protected String fqan = null;
        protected String description = null;
        protected String legacySuffix = null;
        protected String suffix = null;
        protected boolean mappingRequested = false;
        protected boolean isSWManager = false;
        protected boolean isProductionManager = false;
        protected boolean isPilotRole = false;

        
        // Methods
        
        public String getDescription() {
            return (this.description);
        }
        
        public String getFqan() {
            return (this.fqan);
        }
        
        public boolean getMappingRequested() {
            return (this.mappingRequested);
        }
        
        public boolean isSWManager() {
            return (this.isSWManager);
        }
        
        public boolean isProductionManager() {
            return (this.isProductionManager);
        }
        
        public boolean isPilotRole() {
            return (this.isPilotRole);
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public void setFqan(String fqan, String voName) {
            // Remove leading/trailing spaces if any added by mistake...
            this.fqan = fqan.trim();
            // Remove /Role=NULL if speciied in VO ID card
            this.fqan = this.fqan.replaceFirst("/Role=NULL$", "");
            // If the relative FQAN corresponds to the VO name without any role, set it to an empty string.
            // An empty FQAN must not be added to the FQAN list as it is not a real FQAN.
            if ( this.fqan.replaceFirst("^/"+voName,"").length() == 0 ) {
                this.fqan = "";
            }
        }
        
        public void setMappingRequested(String mappingRequested) {
            if ( (mappingRequested != null) && !mappingRequested.contentEquals("0") ) {
                mappingRequested = "true";
            }
            this.mappingRequested = Boolean.parseBoolean(mappingRequested);
        }

        // Set flags used to mark roles processed specifically based VO card data
        public void setReservedRoles(String fqanType) {
            if ( fqanType.equals("Software Manager") ) {
                if ( debugTask ) {
                    System.err.println("    FQAN "+getFqan()+" is declared as the VO software manager");
                }
                this.isSWManager = true;
            } else if ( fqanType.equals("Production Manager") ) {
                if ( debugTask ) {
                    System.err.println("    FQAN "+getFqan()+" is declared as the VO production manager");
                }
                this.isProductionManager = true;
            } else if ( fqanType.equals("Pilot") ) {
                if ( debugTask ) {
                    System.err.println("    FQAN "+getFqan()+" is declared as the VO pilot role");
                }
                this.isPilotRole = true;
            }
        }

        // Set flags used to mark roles processed specifically based on FQAN value.
        // This method does nothing if FQAN is already flagged (based on VO ID card for example)
        public void setReservedRoles(VOConfig voConfig) {
            String relativeFqan = getFqan().replaceFirst("^/"+voConfig.getName(), "");
            if ( this.isSWManager || this.isProductionManager() || this.isPilotRole ) {
                if ( debugTask ) {
                    System.err.println("    FQAN "+getFqan()+" specific role already set (probably from VO ID card)");
                }                
            } else {
                if ( fqanSWManager.contains(relativeFqan) ) {
                    this.isSWManager = true;
                } else if ( fqanProductionManager.contains(relativeFqan) ) {
                    this.isProductionManager = true;
                } else if ( fqanPilot.contains(relativeFqan) ) {
                    this.isPilotRole = true;
                }
            }
        }
        
        // Check if current FQAN is flagged as a specific FQAN using an explicit suffix.
        private String checkSpecificSuffix() {
            String suffix = null;
            if ( isSWManager() ) {
                suffix = softwareManagerSuffix;
            } else if ( isProductionManager() ) {
                suffix = productionManagerSuffix;
            } else if ( isPilotRole() ) {
                suffix = pilotSuffix;
            }
            return (suffix);
        }
        
        /* Remarks on algorithms used to generate account suffix.
         * The original one (implemented in generateLegacyAccountSuffix) was very bad at ensuring suffix uniqueness, 
         * requiring several retries to get a unique suffix and thus making the actual suffix dependent on the FQAN 
         * order which historicall was alphabetical.
         * With the new algorithm (implemented in generateAccountSuffix), there is a very small chance of suffix conflict.
         * Note that changing from old to new suffix is disruptive for the configuration as the accounts must be regenerated.
         */

        private String getAccountSuffix(VOConfig voConfig) throws BuildException {
            if ( this.suffix == null ) {
                // New algorithm is based on relative FQAN to avoid characters similar in every FQAN.
                // This generates a 3-character suffix corresponding to the base26-like encoding of the FQAN hashcode. 
                this.suffix = checkSpecificSuffix();
                if ( this.suffix == null ) {
                    String relativeFqan = getFqan().replaceFirst("^/"+voConfig.getName(), "");
                    this.suffix = VOConfigTask.toBase26(relativeFqan.hashCode());
                    // In (unlikely) case, the suffix is not unique, add the VO name at the end of the relative FQAN
                    if ( ! voConfig.accountSuffixUnique(this.suffix) ) {
                        if ( debugTask ) {
                            System.err.println("    Suffix '"+this.suffix+"' not unique for FQAN '"+relativeFqan+"':  retrying adding VO name");
                        }
                        this.suffix = VOConfigTask.toBase26((relativeFqan+"/"+voConfig.getName()).hashCode());
                    }
                    if ( ! voConfig.accountSuffixUnique(this.suffix) ) {
                        throw new BuildException("VO "+voConfig.getName()+" FQAN '"+getFqan()+"': failed to generate a unique account suffix");
                    }
                    voConfig.addAccountSuffix(this.suffix);
                }
            }
            
            return this.suffix;
        }
        
        private String getLegacyAccountSuffix(VOConfig voConfig) {
            if ( this.legacySuffix == null ) {
                // Generated suffix is based on base26-like conversion of FQAN length and VO ID.
                // Despite this is a very bad choice for uniqueness, it is impossible to change
                // without breaking backward compatibility of generated accounts.
                // New algorithm is implemented as a distinct method.
                this.legacySuffix = checkSpecificSuffix();
                if ( this.legacySuffix == null ) {
                    boolean suffixUnique = false;
                    int j = 0;
                    while ( !suffixUnique ) {
                        if ( debugTask && (j > 0) ) {
                            System.err.println("    Suffix '"+this.legacySuffix+"' not unique for FQAN "+getFqan()+" (attempt "+j+")");
                        }
                        this.legacySuffix = VOConfigTask.toBase26(getFqan().length()+(j*100)) + VOConfigTask.toBase26(voConfig.getId());
                        j++;
                        suffixUnique = voConfig.accountSuffixUnique(this.legacySuffix);
                    }
                    voConfig.addAccountSuffix(this.legacySuffix);
                }                
            }
            
            return (this.legacySuffix);
        }
        
        public void writeTemplate(FileWriter template, VOConfig voConfig) throws IOException {
            String prefix = "";
            if ( !getMappingRequested() ) {
                prefix = "#";
            }
            String description = getDescription();
            if ( isSWManager() ) {
                // SW manager role must have an explicit description, whatever is in the VO card
                description = "SW manager";
            } else if ( isProductionManager() ) {
                // Production manager role must have an explicit description, whatever is in the VO card
                description = "production";
            } else if ( isPilotRole() ) {
                // Pilot role must have an explicit description, whatever is in the VO card
                description = "pilot";
            }
            template.write(prefix+"    nlist('description', '"+description+"',\n");
            template.write(prefix+"          'fqan', '"+getFqan()+"',\n");
            // Both old and new suffix are present in the template as different attributes.
            // The one to use can be choosen at compilation time.
            template.write(prefix+"          'suffix', '"+getLegacyAccountSuffix(voConfig)+"',\n");
            template.write(prefix+"          'suffix2', '"+getAccountSuffix(voConfig)+"',\n");
            template.write(prefix+"         ),\n");
        }
    }

    
    // Class to keep track of VOs using the same accounting prefix
    
    private class AccountPrefixConflict {
        protected LinkedList<String> vos = new LinkedList<String>();
        
        // Methods
        
        public void addVO (String vo) {
            vos.add(vo);
        }
        
        public LinkedList<String> getVOs() {
            return (this.vos);
        }
        
        public String toString() {
            String voListStr = "";
            for (String vo : getVOs()) {
                if ( voListStr.length() > 0 ) {
                    voListStr += " ";
                }
                voListStr += vo;
            }
            return (voListStr);
        }
    }
    
}
