package org.quattor.ant;

import java.io.*;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.Hashtable;
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

		// Write VO configurations to templates
		Set<Entry<String,VOConfig>> voMapEntries = voMap.entrySet();
		for (Entry<String,VOConfig> vo : voMapEntries) {
			String voName = vo.getKey();
			VOConfig voConfig = vo.getValue();
			System.out.println("Writing templates for VO "+voName+" (ID="+voConfig.getId()+")");
		}
	}


	// SAX content handler for VO cards
	
	public class VOCardHandler extends DefaultHandler {
		
		/* VO currently being processed */
		private String voName = null;
		private VOConfig voConfig = null;

		
		/*
		 * Start of document
		 */
		
		@Override
		public void startDocument () throws SAXException {
			voMap = new Hashtable<String,VOConfig>();
		}
		
		
		/**
		 * Start of new element
		 */
		
		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
			if ( qName.equals("VO") ) {
				voName = attributes.getValue("Name");
				if ( voName == null ) {
					throw new SAXException("Invalid configuration: VO has no name");
				}
				voName = voName.toLowerCase();
				System.out.println("Processing VO "+voName);
				voConfig = new VOConfig();
				String voId = attributes.getValue("ID");
				if ( voId == null ) {
					throw new SAXException("Invalid configuration: VO has no Id");
				}
				voConfig.setId(Integer.parseInt(voId));
			}
		}
		
		/**
		 * End of an element
		 */
		
		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
			if ( qName.equals("VO") ) {
				if ( voName != null ) {
					try {
						voMap.put(voName, voConfig);
					} catch (NullPointerException e) {
						throw new SAXException("Internal error: voConfig undefined at the end of VO "+voName+" configuration");
					}
					if ( debugTask ) {
						System.out.println("Finished processing VO "+voName);						
					}
				} else {
					throw new SAXException("Parsing error: end of VO configuration found before start");
				}
			}			
		}
	}

	
	// Class representing a VO
	
	private class VOConfig {
		/* VO ID number */
		int id = 0;
		
		
		// Methods

		private int getId() {
			return (this.id);
		}
		
		private void setId(int id) {
			this.id = id;
		}
		
	}
}
