package org.quattor.ant;

import java.io.File;
import java.io.FileReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

public class NotifyClientTask extends Task {

	/*
	 * The message to set to the client. Either 'ccm' or 'cdb'. The default is
	 * 'ccm'.
	 */
	private String msg = "ccm";

	/*
	 * The port to use for client notification. The default is 7777.
	 */
	private int port = 7777;

	/* The hash to hold the file/modification time map. */
	private HashMap<File, Long> filetimes = new HashMap<File, Long>(100);

	/* The XPath expression to get the host name. */
	static private final XPathExpression findFormat;

	/* The XPath expression to get the host name. */
	static private final XPathExpression findHostPan;

	/* The XPath expression to get the domain name. */
	static private final XPathExpression findDomainPan;

	/* The XPath expression to get the host name. */
	static private final XPathExpression findHostXmlDb;

	/* The XPath expression to get the domain name. */
	static private final XPathExpression findDomainXmlDb;

	/* Print debugging messages */
	private boolean debugTask = false;

	/*
	 * Setup the various XPathExpressions. They are constant and can be shared
	 * among instances of this class.
	 */
	static {
		XPathFactory factory = XPathFactory.newInstance();
		XPath xpath = factory.newXPath();

		try {
			findFormat = xpath.compile("string(/*/@format)");

			findHostPan = xpath.compile("string(/nlist[@name='profile']"
					+ "/nlist[@name='system']" + "/nlist[@name='network']"
					+ "/string[@name='hostname'])");

			findDomainPan = xpath.compile("string(/nlist[@name='profile']"
					+ "/nlist[@name='system']" + "/nlist[@name='network']"
					+ "/string[@name='domainname'])");

			findHostXmlDb = xpath
					.compile("string(/profile/system/network/hostname)");

			findDomainXmlDb = xpath
					.compile("string(/profile/system/network/domainname)");
		} catch (javax.xml.xpath.XPathExpressionException xpee) {
			xpee.printStackTrace();
			throw new RuntimeException("Cannot create XPath expressions.");
		}
	}

	/*
	 * Method used by ant to execute this task.
	 */
	public void execute() throws BuildException {

		LinkedList<DatagramPacket> packets = new LinkedList<DatagramPacket>();

		// Loop over all of the files.
		for (File file : filetimes.keySet()) {
			Long time = filetimes.get(file);
			String host = getFullHostname(file);

			try {

				if (host != null) {
					packets.add(constructPacket(host, msg, time));
				}

			} catch (java.io.UnsupportedEncodingException uee) {
				System.err.println("Unsupported uncoding US-ASCII!");
			} catch (java.net.UnknownHostException uhe) {
				System.err.println("Unknown host: " + host);
			}
		}

		// Notify the clients.
		notify(packets);
	}

	/**
	 * Setting this flag will print debugging information from the task itself.
	 * This is primarily useful if one wants to debug a build using the command
	 * line interface.
	 * 
	 * @param debugTask
	 *            flag to print task debugging information
	 */
	public void setDebugTask(boolean debugTask) {
		this.debugTask = debugTask;
	}

	/*
	 * Set the message to send to the client. This should either be the string
	 * 'ccm' or 'cdb'.
	 * 
	 * @param msg String of either 'ccm' or 'cdb'
	 */
	public void setMessage(String msg) {
		if ((!"ccm".equals(msg)) && (!"cdb".equals(msg))) {
			throw new IllegalArgumentException(
					"Message must be 'ccm' or 'cdb'.");
		}
		this.msg = msg;
	}

	/*
	 * Set the port number to use to notify client. The default is 7777.
	 * 
	 * @param int port number to use to notify client
	 */
	public void setPort(int port) {
		this.port = port;
	}

	/*
	 * Support nested fileset elements. This is called by ant only after all of
	 * the children of the fileset have been processed. Collect all of the
	 * selected files from the fileset.
	 * 
	 * @param fileset a configured FileSet
	 */
	public void addConfiguredFileSet(FileSet fileset) {
		if (fileset != null)
			addFiles(fileset);
	}

	/*
	 * Collect all of the files listed within enclosed fileSet tags, get the
	 * modification times, and add each pair to the given filetimes hash.
	 * 
	 * @param fs FileSet from which to get the file names
	 */
	private void addFiles(FileSet fs) {

		// Get the files included in the fileset.
		DirectoryScanner ds = fs.getDirectoryScanner(getProject());

		// The base directory for all files.
		File basedir = ds.getBasedir();

		// Loop over each file, creating a File object, and collecting
		// the modification time in SECONDS since the epoch. (Java by
		// default uses milliseconds since the epoch.)
		// for (String f : ds.getIncludedFiles()) {
		String[] temp = ds.getIncludedFiles();
		for (int i = 0; i < temp.length; i++) {
			String f = temp[i];

			File file = new File(basedir, f);
			Long time = new Long(file.lastModified() / 1000);
			filetimes.put(file, time);
		}
	}

	/*
	 * Read the given XML machine profile and extract the full hostname from the
	 * given host and domain.
	 * 
	 * @param file a File referencing the XML machine profile
	 * 
	 * @return a String containing the full hostname or null if an error occurs
	 */
	private String getFullHostname(File file) {

		String fullname = null;

		if (file != null) {
			try {
				FileReader reader = new FileReader(file);
				InputSource is = new InputSource(reader);

				DocumentBuilderFactory factory = DocumentBuilderFactory
						.newInstance();
				DocumentBuilder builder = factory.newDocumentBuilder();
				Document doc = builder.parse(is);

				String fmt = findFormat.evaluate(doc);

				String host = null;
				String domain = null;
				if (fmt == null || "pan".equals(fmt)) {
					host = findHostPan.evaluate(doc);
					domain = findDomainPan.evaluate(doc);
				} else if ("xmldb".equals(fmt)) {
					host = findHostXmlDb.evaluate(doc);
					domain = findDomainXmlDb.evaluate(doc);
				}

				if (host != null && domain != null) {
					fullname = host + "." + domain;
				}

			} catch (java.io.FileNotFoundException fnfe) {
				System.err.println(fnfe.getMessage());
			} catch (javax.xml.parsers.ParserConfigurationException pce) {
				System.err.println(pce.getMessage());
			} catch (org.xml.sax.SAXException se) {
				System.err.println(se.getMessage());
			} catch (java.io.IOException ioe) {
				System.err.println(ioe.getMessage());
			} catch (javax.xml.xpath.XPathExpressionException xpee) {
				System.err.println(xpee.getMessage());
			}
		}
		return fullname;
	}

	/*
	 * Construct a datagram packet from the given information.
	 * 
	 * @param String full hostname of client @param String message (either 'ccm'
	 * or 'cdb') @param Long modification time in seconds since the epoch
	 */
	private DatagramPacket constructPacket(String host, String msg, Long time)
			throws java.io.UnsupportedEncodingException,
			java.net.UnknownHostException {

		DatagramPacket packet = null;

		if (host != null) {

			// The payload of the message. This is the three
			// character message followed by a null byte and then
			// the modification time in seconds since the epoch.
			byte[] payload = (msg + '\0' + time.toString())
					.getBytes("US-ASCII");

			// The address.
			InetAddress ip = InetAddress.getByName(host);

			// Create the datagram packet.
			packet = new DatagramPacket(payload, payload.length, ip, port);

		}

		return packet;
	}

	/*
	 * Send the list of packets to notify clients.
	 * 
	 * @param LinkedList containing DatagramPackets to send
	 */
	private void notify(LinkedList<DatagramPacket> packets) {

		DatagramSocket socket = null;

		try {

			// Create a socket for sending datagram (UDP) messages.
			socket = new DatagramSocket();

			for (DatagramPacket packet : packets) {
				try {
					System.out.println("Notifying: " + packet.getAddress()
							+ ":" + packet.getPort());
					socket.send(packet);
				} catch (java.io.IOException ioe) {
					System.err.println(ioe.getMessage());
				}
			}

		} catch (java.net.SocketException se) {
			System.err.println(se.getMessage());
		} finally {

			// Make sure that the socket is closed!
			if (socket != null)
				socket.close();
		}
	}

}
