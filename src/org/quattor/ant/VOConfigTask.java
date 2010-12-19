package org.quattor.ant;

import java.io.*;
import java.net.URL;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;
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
    protected Hashtable<String,VOConfig> voMap = new Hashtable<String,VOConfig>();;

    /* Hash table of all defined VOMS servers.
     * Used to check consistency of VOMS server attributes across VOs.
     */
    protected Hashtable<String,VOMSServer>vomsServers = new Hashtable<String,VOMSServer>();;
    

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
        
        // Write VO configurations to templates
        Set<Entry<String,VOConfig>> voMapEntries = voMap.entrySet();
        Set<Entry<String,VOMSServer>> vomsServersEntries = vomsServers.entrySet();
        for (String branch : ds.getIncludedDirectories()) {
            System.out.println("Updating templates in branch "+branch);
            for (Entry<String,VOConfig> vo : voMapEntries) {
                vo.getValue().writeVOTemplate(templateBasedir+"/"+branch);
            }
            for (Entry<String,VOMSServer> server : vomsServersEntries) {
                server.getValue().updateVOMSServerTemplate(templateBasedir+"/"+branch);
            }
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
            if ( qName.equals("VO") ) {
                String voName = attributes.getValue("Name");
                if ( voName == null ) {
                    throw new SAXException("Invalid configuration: VO has no name");
                }
                voName = voName.toLowerCase();
                System.out.println("Retrieving configuration for VO "+voName);
                voConfig = new VOConfig();
                String voId = attributes.getValue("ID");
                if ( voId == null ) {
                    throw new SAXException("Invalid configuration: VO has no Id");
                }
                voConfig.setName(voName);
                voConfig.setId(Integer.parseInt(voId));
                
            } else if ( qName.equals("VOMSServers") ) {
                sectionVOMSServers = true;
                
            } else if ( qName.equals("GroupsAndRoles") ) {
                sectionGroupsRoles = true;
                
            } else if ( sectionVOMSServers ) {
                if ( qName.equals("VOMSServer") ) {
                    vomsServer = new VOMSServer();        
                    vomsEndpoint = new VOMSEndpoint();
                } else {
                    // This will enable collection/concatenation of data in characters()
                    data = "";
                }
                
            } else if ( sectionGroupsRoles ) {
                if ( qName.equals("GroupAndRole") ) {
                    fqan = new VOMSFqan();
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
            if ( qName.equals("VO") ) {
                if ( voConfig.getName() != null ) {
                    try {
                        voMap.put(voConfig.getName(), voConfig);
                    } catch (NullPointerException e) {
                        throw new SAXException("Internal error: voMap or voConfig undefined at the end of VO "+voConfig.getName()+" configuration");
                    }
                    if ( debugTask ) {
                        System.out.println("Finished processing VO "+voConfig.getName());                        
                    }
                } else {
                    throw new SAXException("Parsing error: end of VO configuration found before start");
                }

            } else if ( qName.equals("VOMSServers") ) {
                sectionVOMSServers = false;

            } else if ( qName.equals("GroupsAndRoles") ) {
                sectionGroupsRoles = false;
                
            } else if ( sectionVOMSServers ) {
                // Check the VOMS server has not been defined yet by another VO or that attributes are consistent
                // A unique VOMS server is identified by host+port combination
                String VOMSServerKey = vomsServer.getHost() + ":" + Integer.toString(vomsServer.getPort());
                if ( qName.equals("VOMSServer") ) {
                    if ( vomsServers.containsKey(VOMSServerKey) ) {
                        if ( debugTask ) {
                            System.err.println("VOMS server '"+VOMSServerKey+"' already defined: checking attribute consistency.");
                        }
                        if ( vomsServer.getCert() != vomsServers.get(VOMSServerKey).getCert() ) {
                            if ( vomsServer.getCertExpiry().after(vomsServers.get(VOMSServerKey).getCertExpiry())) {
                                System.err.println("    WARNING: VOMS server '"+VOMSServerKey+"' already defined with an older certificate, updating it.");
                                vomsServers.get(VOMSServerKey).setCertExpiry(vomsServer.getCertExpiry());
                            } else if ( vomsServer.getCertExpiry().before(vomsServers.get(VOMSServerKey).getCertExpiry())) {
                                System.err.println("    WARNING: VOMS server '"+VOMSServerKey+"' already defined with a newer certificate, keeping previous one.");                                
                            }
                        }
                    } else {
                        if ( debugTask ) {
                            System.err.println("Adding VOMS server '"+VOMSServerKey+"' to global VOMS server list.");
                        }
                        vomsServers.put(VOMSServerKey,vomsServer);
                    }
                    vomsEndpoint.setServer(vomsServers.get(VOMSServerKey));
                    voConfig.addVomsEndpoint(vomsEndpoint);                    
                } else {
                    if ( qName.equals("HOSTNAME") ) {
                        vomsServer.setHost(data);
                    } else if ( qName.equals("HTTPS_PORT") ) {
                        vomsServer.setPort(data);
                    } else if ( qName.equals("VOMS_PORT") ) {
                        vomsEndpoint.setPort(data);
                    } else if ( qName.equals("ServerEndpoint") ) {
                        vomsEndpoint.setEndpoint(data);
                    } else if ( qName.equals("CertificatePublicKey") ) {
                        vomsServer.setCert(data);
                    } else if ( qName.equals("CERTIFICATE_EXPIRATION_DATE") ) {
                        vomsServer.setCertExpiry(data);
                    } else if ( qName.equals("IS_VOMSADMIN_SERVER") ) {
                        vomsEndpoint.setVomsAdminEnabled(data);
                    } else if ( qName.equals("DN") ) {
                        vomsServer.setDN(data);
                    }
                    // Disable collection of data
                    data = null;
                }

            } else if ( sectionGroupsRoles ) {
                if ( qName.equals("GroupAndRole") ) {
                    // An empty value for the FQAN means this FQAN must not be added to the list.
                    if ( fqan.getFqan().length() != 0 ) {
                        voConfig.fqanList.put(fqan.getFqan(),fqan);
                    }
                } else {
                    if ( qName.equals("GROUP_ROLE") ) {
                        fqan.setFqan(data,voConfig.getName());
                        fqan.setReservedRoles(voConfig.getName());
                    } else if ( qName.equals("DESCRIPTION") ) {
                        fqan.setDescription(data);
                    } else if ( qName.equals("IS_GROUP_USED") ) {
                        fqan.setMappingRequested(data);
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

        public int getId() {
            return (this.id);
        }

        public String getName() {
            return (this.name);
        }
        
        public LinkedList<VOMSEndpoint> getVomsEndpointList() {
            return (this.vomsEndpointList);
        }

        public void setId(int id) {
            this.id = id;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String toStr() {
            String configStr = "";
            for (VOMSEndpoint endpoint : vomsEndpointList) {
                configStr += "    VOMS Server: "+endpoint.getEndpoint()+" (VOMS port="+endpoint.getPort()+", voms-admin="+endpoint.getVomsAdminEnabled()+")\n";
            }                
            return (configStr);
        }
        
        private void writeVOTemplate(String templateBranch) throws BuildException {            
            if ( debugTask ) {
                System.err.println("VO configuration for VO "+getName()+" (ID="+getId()+"):\n"+toStr());
            }
            
            String voParamsNS = paramsTplNS + "/" + getName();
            String voParamsTpl = templateBranch + "/" + voParamsNS + ".tpl";
            System.out.println("Writing template for VO "+getName()+" ("+voParamsTpl+")");

            try {
                FileWriter template = new FileWriter(voParamsTpl);
                template.write("structure template "+voParamsNS+";\n\n");
                template.write("'name' ?= '"+getName()+"';\n");
                template.write("\n");
                template.write("'voms_servers' ?= list(\n");
                if ( getVomsEndpointList().isEmpty() ) {
                    System.err.println("    WARNING: VO "+getName()+" has no VOMS endpoint defined");
                }
                for (VOMSEndpoint vomsEndpoint : getVomsEndpointList()) {
                    vomsEndpoint.writeTemplate(template);
                }
                template.write(");\n");
                template.write("'voms_mappings' ?= list(\n");
                if ( debugTask && fqanList.isEmpty() ) {
                    System.err.println("    INFO: VO "+getName()+" has no specific FQAN defined");
                }
                Set<String> sortedFqanList;
                if ( legacySuffixAlgorithm ) {
                    // Alphabetical order
                    sortedFqanList = new TreeSet<String>();
                    for (String key : fqanList.keySet()) {
                        sortedFqanList.add(key);
                    }
                } else {
                    // Insertion order
                    sortedFqanList = fqanList.keySet();
                }
                for (String key : sortedFqanList) {
                    VOMSFqan fqan = fqanList.get(key);
                    fqan.writeTemplate(template,this);
                }
                template.write(");\n");
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

        public void writeTemplate(FileWriter template) throws IOException {
            template.write("    nlist('name', '"+getServer().getHost()+"',\n");
            template.write("          'host', '"+getServer().getHost()+"',\n");
            template.write("          'port', "+getServer().getPort()+",\n");
            if ( !getVomsAdminEnabled() ) {
                template.write("          'type', list('voms-only'),\n");
            }
            template.write("         ),\n");
        }
    }
    
    
    // Class representing a VOMS server

    private class VOMSServer {
        protected String host = null;
        protected int port = 8443;
        protected String cert = null;
        protected Date certExpiry = null;
        protected String dn = null;
        protected Pattern certDelimiterPattern = Pattern.compile("^(\\w+)\\s*;");
        protected Pattern certDeclarationPattern = Pattern.compile("^\\s*('|\")cert\1\\s*\\??=\\s*");
        
        
        // Methods

        public String getCert() {
            return (this.cert);
        }
        
        public Date getCertExpiry () {
            return (this.certExpiry);
        }

        public String getDN() {
            return (this.dn);
        }

        public String getHost() {
            return (this.host);
        }

        public int getPort() {
            return (this.port);
        }

        public void setCert(String cert) {
            this.cert = cert;
        }

        public void setCertExpiry(String expiry) throws SAXException {
            SimpleDateFormat expiryFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            try {
                this.certExpiry = expiryFormat.parse(expiry);
            } catch (ParseException e) {
                throw new SAXException("Failed to parse VOMS server "+getHost()+" certificate expiry date ("+expiry+")");
            }
        }

        public void setCertExpiry(Date expiry) {
            this.certExpiry = expiry;
        }

        public void setDN(String dn) {
            this.dn = dn;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public void setPort (String port) {
            this.port = Integer.parseInt(port);
        }
        
        private void updateVOMSServerTemplate(String templateBranch) throws BuildException {            
            String certParamsNS = certsTplNS + "/" + getHost();
            String certParamsTpl = templateBranch + "/" + certParamsNS + ".tpl";
            File templateFile = new File(certParamsTpl);
            String oldCert = "";
            
            // If a previous version of the template exists, retrieve current certificate to 
            // add it as 'oldcert' in the updated template
            if ( templateFile.exists() ) {
                System.out.println("Updating template for VOMS server "+getHost()+" ("+certParamsTpl+")");
                try {
                    Scanner templateScanner = new Scanner(templateFile);
                    String existingCert = templateScanner.findWithinHorizon(certDeclarationPattern, 0);
                    Matcher delimiterMatcher = certDelimiterPattern.matcher(existingCert);
                    if ( delimiterMatcher.matches() ) {
                        String delimiter = delimiterMatcher.group(1);
                        templateScanner.useDelimiter(delimiter+";*");
                        existingCert = templateScanner.next();
                    } else {
                        if ( debugTask ) {
                            System.err.println("WARNING: failed to match certificate delimiter");
                        }
                    }
                } catch (FileNotFoundException e) {
                    // Should not happen as file existence was tested just before
                    throw new BuildException("Internal error: file not found in VOMSServer.updateVOMSServerTemplate()");
                } catch (NoSuchElementException e) {
                    if ( debugTask ) {
                        System.err.println("Failed to retrieve current certificate in exiting template "+certParamsTpl);
                    }
                }
            } else {
                System.out.println("Writing template for VOMS server "+getHost()+" ("+certParamsTpl+")");                
            }

            try {
                FileWriter template = new FileWriter(templateFile);
                template.write("structure template "+certParamsNS+";\n\n");
                template.write("'cert' ?= <<EOF;\n");
                template.write(getCert());
                template.write("EOF\n\n");
                if ( oldCert.length() > 0 ) {
                    template.write("'oldcert' ?= <<EOF;\n");
                    template.write(oldCert);
                    template.write("EOF\n\n");                    
                }
                template.close();
            } catch (IOException e){
                throw new BuildException("Error writing template for VO "+getHost()+" ("+certParamsTpl+")\n"+e.getMessage());
            }            
        }
    }
    
    // VOMS FQAN
    
    private class VOMSFqan {
        protected String fqan = null;
        protected String description = null;
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
        
        public String getAccountSuffix (VOConfig voConfig) {
            if ( this.suffix == null ) {
                if ( legacySuffixAlgorithm ) {
                    this.suffix = generateLegacyAccountSuffix(voConfig);                    
                } else {
                    this.suffix = generateAccountSuffix(voConfig);                    
                }
            }
            return (this.suffix);
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

        // Set flags used to mark roles processed specifically
        public void setReservedRoles(String voName) {
            String relativeFqan = getFqan().replaceFirst("^/"+voName, "");
            if ( fqanSWManager.contains(relativeFqan) ) {
                this.isSWManager = true;
            } else if ( fqanProductionManager.contains(relativeFqan) ) {
                this.isProductionManager = true;
            } else if ( fqanPilot.contains(relativeFqan) ) {
                this.isPilotRole = true;
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
        
        private String generateAccountSuffix(VOConfig voConfig) throws BuildException {
            String suffix = checkSpecificSuffix();

            // New algorithm is based on relative FQAN to avoid characters similar in every FQAN.
            // This generates a 3-character suffix corresponding to the base26-like encoding of the FQAN hashcode. 
            if ( suffix == null ) {
                String relativeFqan = getFqan().replaceFirst("^/"+voConfig.getName(), "");
                suffix = VOConfigTask.toBase26(relativeFqan.hashCode());
                // In (unlikely) case, the suffix is not unique, add the VO name at the end of the relative FQAN
                if ( ! voConfig.accountSuffixUnique(suffix) ) {
                    if ( debugTask ) {
                        System.err.println("Suffix '"+suffix+"' not unique for FQAN '"+relativeFqan+"':  retrying adding VO name");
                    }
                    suffix = VOConfigTask.toBase26((relativeFqan+"/"+voConfig.getName()).hashCode());
                }
                if ( ! voConfig.accountSuffixUnique(suffix) ) {
                    throw new BuildException("VO "+voConfig.getName()+" FQAN '"+getFqan()+"': failed to generate a unique account suffix");
                }
                voConfig.addAccountSuffix(suffix);
            }

            return (suffix);
        }
        
        private String generateLegacyAccountSuffix(VOConfig voConfig) {
            String suffix = checkSpecificSuffix();

            // Generated suffix is based on base26-like conversion of FQAN length and VO ID.
            // Despite this is probably not the best way to generate a unique suffix inside the VO,
            // this is difficult to change it without breaking backward compatibility for configuration
            // based on these templates.
            if ( suffix == null ) {
                boolean suffixUnique = false;
                int j = 0;
                while ( !suffixUnique ) {
                    if ( debugTask && (j > 0) ) {
                        System.err.println("Suffix '"+suffix+"' not unique for FQAN "+getFqan()+" (attempt "+j+")");
                    }
                    suffix = VOConfigTask.toBase26(getFqan().length()+(j*100)) + VOConfigTask.toBase26(voConfig.getId());
                    j++;
                    suffixUnique = voConfig.accountSuffixUnique(suffix);
                }
                voConfig.addAccountSuffix(suffix);
            }
            return (suffix);
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
            template.write(prefix+"          'suffix', '"+getAccountSuffix(voConfig)+"',\n");
            template.write(prefix+"         ),\n");
        }
    }
}
