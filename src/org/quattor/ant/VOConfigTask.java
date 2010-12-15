package org.quattor.ant;

import java.io.*;
import java.net.URL;
import java.net.MalformedURLException;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.DirSet;

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


	// Methods

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

	public void execute() throws BuildException {
		
		SAXParserFactory factory = SAXParserFactory.newInstance();
		try {
			URL voIdCardsUrl = new URL(voIdCardsUri);
			InputStream urlstream = voIdCardsUrl.openStream();
			SAXParser parser = factory.newSAXParser();
			parser.parse(urlstream, new VOCardHandler());		
		} catch (MalformedURLException e) {
			System.err.println("Invalid format used for specifying the source of VO ID cards (voIdCardsUri): "+voIdCardsUri+"\n");
			throw new BuildException("BUILD FAILED : " + e.getMessage());
		} catch (IOException e) {
			System.err.println("Failed to open VO ID card source ("+voIdCardsUri+")\n");
			throw new BuildException("BUILD FAILED : " + e.getMessage());			
		} catch (Exception e) {
			System.err.println("Error parsing VO ID cars ("+voIdCardsUri+")\n");
			throw new BuildException("BUILD FAILED : " + e.getMessage());						
		}

	}


	// SAX content handler for VO cards
	
	private class VOCardHandler extends DefaultHandler {
		
	}
}
