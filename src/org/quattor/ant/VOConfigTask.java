package org.quattor.ant;

import java.io.*;
import java.net.URL;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
	private DirSet configDirs = null;

	/* Namespace (relative directory) to use for templates containing VO parameters */
	private String paramsTplNS = null;

	/* Namespace (relative directory) to use for templates containing VOMS server certificates */
	private String certsTplNS = null;

	/* Namespace (relative directory) to use for templates containing VO site-specific params */
	private String siteParamsTplNS = null;

	/* URI for VO ID card source */
	private String voIdCardsUri = null;

	/* Control printing of debugging messages in this task */
	private boolean debugTask = false;

	/* Configuration of VOs retrieved from VO ID cards.
	 * This is a hash with one entry per VO : the key is the VO name.
	 */
	private Hashtable<String,VOConfig> voMap = null;

	/* Hash table of all defined VOMS servers.
	 * Used to check consistency of VOMS server attributes across VOs.
	 */
	private Hashtable<String,VOMSServer>vomsServers = null;
	

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
			System.err.println("Invalid format used for specifying the source of VO ID cards (voIdCardsUri): "+voIdCardsUri);
			throw new BuildException("BUILD FAILED : " + e.getMessage());
		} catch (IOException e) {
			System.err.println("Failed to open VO ID card source ("+voIdCardsUri+")");
			throw new BuildException("BUILD FAILED : " + e.getMessage());			
		} catch (Exception e) {
			System.err.println("Error parsing VO ID cars ("+voIdCardsUri+")");
			throw new BuildException("BUILD FAILED : " + e.getMessage());						
		}

		// Retrieve list of template branches to update
		DirectoryScanner ds = configDirs.getDirectoryScanner(getProject());
		File templateBasedir = ds.getBasedir();
		
		// Write VO configurations to templates
		Set<Entry<String,VOConfig>> voMapEntries = voMap.entrySet();
		Set<Entry<String,VOMSServer>> vomsServersEntries = vomsServers.entrySet();
		for (String branch : ds.getIncludedDirectories()) {
			System.err.println("Updating templates in branch "+branch);
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
		private VOConfig voConfig = null;

		/* Context variables */
		private boolean sectionVOMSServers = false;
		private VOMSServer vomsServer = null;
		private VOMSEndpoint vomsEndpoint = null;
		String data = null;

		/*
		 * Start of document
		 */

		@Override
		public void startDocument () throws SAXException {
			voMap = new Hashtable<String,VOConfig>();
			vomsServers = new Hashtable<String,VOMSServer>();
		}


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

			} else if ( sectionVOMSServers ) {
				if ( qName.equals("VOMSServer") ) {
					vomsServer = new VOMSServer();		
					vomsEndpoint = new VOMSEndpoint();
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
					if ( data != null ) {
						data = null;
					}
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
		private String name = null;
		
		/* VO ID number */
		private int id = 0;
		
		/* Account prefix */
		private String accountPrefix = null;

		/* List of VOMS servers */
		private LinkedList<VOMSEndpoint> vomsEndpointList = new LinkedList<VOMSEndpoint>();


		// Methods

		public void addVomsEndpoint(VOMSEndpoint vomsEndpoint) {
			vomsEndpointList.add(vomsEndpoint);
		}

		public int getId() {
			return (this.id);
		}

		public String getName() {
			return (this.name);
		}
		
		public LinkedList<VOMSEndpoint> getVomsServerList() {
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
				System.out.println("VO configuration for VO "+getName()+" (ID="+getId()+"):\n"+toStr());
			}
			
			String voParamsNS = paramsTplNS + "/" + getName();
			String voParamsTpl = templateBranch + "/" + voParamsNS + ".tpl";
			System.out.println("Writing template for VO "+getName()+" ("+voParamsTpl+")");

			try {
				FileWriter template = new FileWriter(voParamsTpl);
				template.write("unique template "+voParamsNS+";\n\n");
				template.write("'name' ?= '"+getName()+"'\n");
				template.write("\n");
				template.write("'voms_servers' ?= list(\n");
				if ( getVomsServerList().isEmpty() ) {
					System.err.println("    WARNING: VO "+getName()+" has no VOMS endpoint defined");
				}
				for (VOMSEndpoint vomsServer : getVomsServerList()) {
					template.write("                       nlist('name', '"+vomsServer.server.host+"',\n");
					template.write("                             'host', '"+vomsServer.server.host+"',\n");
					template.write("                             'port', '"+vomsServer.port+"',\n");
					if ( !vomsServer.getVomsAdminEnabled() ) {
						template.write("                             'type', 'voms-only',\n");
					}
					template.write("                            ),\n");
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
		private VOMSServer server = null;
		private int port;
		private String endpoint = null;
		private boolean vomsAdminEnabled = true;
		
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
}
	
	
	// Class representing a VOMS server

	private class VOMSServer {
		private String host = null;
		private int port = 8443;
		private String cert = null;
		private Date certExpiry = null;
		private String dn = null;

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
			System.out.println("Writing template for VOMS server "+getHost()+" ("+certParamsTpl+")");

			try {
				FileWriter template = new FileWriter(certParamsTpl);
				template.write("unique template "+certParamsNS+";\n\n");
				template.write("'name' ?= '"+getHost()+"'\n");
				template.write("'cert' ?= <<EOF;");
				template.write(getCert());
				template.write("EOF\n");
				template.close();
			} catch (IOException e){
				throw new BuildException("Error writing template for VO "+getHost()+" ("+certParamsTpl+")\n"+e.getMessage());
			}			
		}
	}
}
