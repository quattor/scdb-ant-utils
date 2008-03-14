package org.quattor.ant;

import java.io.*;
import java.net.URL;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

/**
 * This task creates pan templates from data given by the CIC portal
 * 
 * @author duprilot
 */
public class VOConfigTask extends Task {

	/**
	 * This handler parse the xml file given by the CIC portal
	 * 
	 * @author duprilot
	 */
	public class MyHandler extends DefaultHandler {
		/*
		 * Constructor.
		 * 
		 */
		public MyHandler() {
			super();
		}

		/*
		 * Error thrower.
		 * 
		 */
		public void error(SAXParseException e) throws SAXParseException {
			throw e;
		}

		/*
		 * The document is in parsing.
		 * 
		 */
		public void startDocument() throws SAXException {

		}

		/*
		 * The parsing of the document is finished.
		 * 
		 */
		public void endDocument() throws SAXException {
		}

		/*
		 * Opening of an element.
		 * 
		 */
		public void startElement(String namespaceURI, String simpleName,
				String qualifiedName, Attributes attrs) throws SAXException {
			if (qualifiedName.equals("VO")) {
				VOname = attrs.getValue("Name");
				VOname = VOname.toLowerCase();
				VOid = attrs.getValue("ID");
				boolean isShort = false;
				isShort = verifyShortNamedFileExists(VOname);
				if (isShort) {
					fileTplName = getFileName(VOname, false, true);
				} else {
					fileTplName = getFileName(VOname, false, false);
				}
				bw = initFile(fileTplName);
				String siteParamsFileName = getNameSiteParamsDir();
				String siteParamName = VOname;
				siteParamsFileName = siteParamsFileName.concat("/").concat(
						siteParamName);
				if (isShort) {
					write("structure template "+nameAliasDirTpl+"/" + VOname + ";", bw);
				} else {
					write("structure template "+nameParamDirTpl+"/" + VOname + ";", bw);
				}
				write("", bw);
				write("include {if_exists('" + siteParamsFileName + "')};", bw);
				write("", bw);
			} else {
				buffer = new StringBuffer();
			}
		}

		/*
		 * The element is closed.
		 * 
		 */
		public void endElement(String namespaceURI, String simpleName,
				String qualifiedName) throws SAXException {
			if (qualifiedName.equals("VO")) {
				// String nList = initNList(VOname);
				int Id = Integer.parseInt(VOid);
				String accountPrefix = createAccount(VOname, Id);
				// write(nList, bw);
				write("\"name\" ?= '" + VOname + "';", bw);
				write("\"account_prefix\" ?= '" + accountPrefix + "';", bw);
				write("", bw);
				if ((hostname != null) && (port != null)) {
					write("\"voms_servers\" ?= nlist(\"name\", '" + hostname
							+ "',", bw);
					write("\t\t\t\"host\", '" + hostname + "',", bw);
					write("\t\t\t\"port\", " + port + ",", bw);
					write("\t\t\t);", bw);
					write("", bw);
					if ((roleAdmin != null) || (roleProd != null)
							|| (roleAtl != null) || (roleSwAdmin != null)
							|| (roleSwMan != null)) {
						write("\"voms_roles\" ?= list(", bw);
						if (roleAdmin != null) {
							write(
									"\t\t\tnlist(\"description\", \"SW manager\",",
									bw);
							write("\t\t\t\t\"fqan\", \"lcgadmin\",", bw);
							write("\t\t\t\t\"suffix\", \"s\"),", bw);
						}
						if (roleSwAdmin != null) {
							write(
									"\t\t\tnlist(\"description\", \"SW manager\",",
									bw);
							write("\t\t\t\t\"fqan\", \"swadmin\",", bw);
							write("\t\t\t\t\"suffix\", \"s\"),", bw);
						}
						if (roleProd != null) {
							write(
									"\t\t\tnlist(\"description\", \"production\",",
									bw);
							write("\t\t\t\t\"fqan\", \"production\",", bw);
							write("\t\t\t\t\"suffix\", \"p\"),", bw);
						}
						if (roleAtl != null) {
							write("\t\t\tnlist(\"description\", \"ATLAS\",", bw);
							write("\t\t\t\t\"fqan\", \"atlas\",", bw);
							write("\t\t\t\t\"suffix\", \"atl\"),", bw);
						}
						if (roleSwMan != null) {
							write(
									"\t\t\tnlist(\"description\", \"SW manager\",",
									bw);
							write("\t\t\t\t\"fqan\", \"SoftwareManager\",", bw);
							write("\t\t\t\t\"suffix\", \"s\"),", bw);
						}
						write(");", bw);
						roleProd = null;
						roleAdmin = null;
						roleAtl = null;
						roleSwAdmin = null;
						roleSwMan = null;
					}
					write("", bw);
				}
				write("\"proxy\" ?= '" + proxy + "';", bw);
				write("\"nshosts\" ?= '" + nshosts + "';", bw);
				write("\"lbhosts\" ?= '" + lbhosts + "';", bw);
				write("", bw);
				write("\"pool_size\" ?= " + pool_size + ";", bw);
				base_uid = Id * 1000;
				write("\"base_uid\" ?= " + base_uid + ";", bw);
				// write(");", bw);
				hostname = null;
				port = null;
				certificat = null;
				closeFile(fileTplName, bw);
			} else if (qualifiedName.equals("GROUP_ROLE")) {
				Matcher m = padmin.matcher(buffer.toString());
				Matcher mbis = padmin2.matcher(buffer.toString());
				Matcher mter = padmin3.matcher(buffer.toString());
				Matcher mqua = padmin4.matcher(buffer.toString());
				Matcher m2 = pprod.matcher(buffer.toString());
				Matcher m3 = patlas.matcher(buffer.toString());
				Matcher m4 = pswadmin.matcher(buffer.toString());
				Matcher m5 = pswman.matcher(buffer.toString());
				if ((m.find()) || (mbis.find()) || (mter.find()) || (mqua.find())) {
					roleAdmin = buffer.toString();
				} else if (m2.find()) {
					roleProd = buffer.toString();
				} else if (m3.find()) {
					roleAtl = buffer.toString();
				} else if (m4.find()) {
					roleSwAdmin = buffer.toString();
				} else if (m5.find()) {
					roleSwMan = buffer.toString();
				}
				buffer = null;
			} else if (qualifiedName.equals("HOSTNAME")) {
				hostname = buffer.toString();
				buffer = null;
			} else if (qualifiedName.equals("VOMS_PORT")) {
				port = buffer.toString();
				buffer = null;
			} else if ((qualifiedName.equals("CertificatePublicKey"))
					&& (!buffer.toString().equals(""))) {
				certificat = buffer.toString();
				if (certificat.endsWith("\n")) {
					certificat = certificat.substring(0,
							(certificat.length()) - 2);
				}
				writeCert();
				buffer = null;
			} else if (qualifiedName.equals("VOMSServers")) {
				if (hostname == null) {
					System.err
							.println("VOMSServer attribute doesn't exist for VO "
									+ VOname);
				}
			}
		}

		public void characters(char buf[], int offset, int len)
				throws SAXException {
			String s = new String(buf, offset, len);
			if (buffer != null) {
				buffer.append(s);
			}
		}
	}

	// DECLARATION DE VARIABLES

	/* the buffer containing data given in the XML document */
	private StringBuffer buffer;

	/* the name of the root directory */
	private static String configRootDir = null;

	/* the name of the directory containing generated templates */
	private static String nameParamDirTpl = null;

	/* the name of the directory containing generated certificates templates */
	private static String nameCertDirTpl = null;

	/* the name of the url wherre to find the XML document */
	private static String urlFile = null;

	/* the name of the directory containing customization templates */
	private static String nameSiteParamsDir = null;

	/*
	 * the name of the directory containing generated templates for alias named
	 * VOs
	 */
	private static String nameAliasDirTpl = null;

	/* the name of the VO */
	private static String VOname = null;

	/* the id of the VO */
	private static String VOid = null;

	/* the full path of the template */
	private static String fileTplName = null;

	/* the BufferedWriter associated to the VO template */
	private static BufferedWriter bw = null;

	/* the BufferedWriter associated to the certificat template */
	private static BufferedWriter bwCert = null;

	/* the patterns used to collect the roles */
	private static final Pattern padmin = Pattern.compile("Role=lcgadmin",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern padmin2 = Pattern.compile("/admin",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern padmin3 = Pattern.compile("Role=VO-Admin",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern padmin4 = Pattern.compile("Role=VOAdmin",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern pswman = Pattern.compile(
			"Role=SoftwareManager", Pattern.CASE_INSENSITIVE);

	private static final Pattern pswadmin = Pattern.compile("Role=swadmin",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern pprod = Pattern.compile("Role=production",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern patlas = Pattern.compile("Role=atlas",
			Pattern.CASE_INSENSITIVE);

	/* the hostname of the server */
	private static String hostname = null;

	/* the readen certificat */
	private static String certificat = null;

	/* the BufferedWriter associated to the certificat template */
	private static boolean certExists = false;

	/* Determines weather or not the certificat exists */
	private static String port = null;

	/* the Strings containing the roles */
	private static String roleAdmin = null;

	private static String roleSwAdmin = null;

	private static String roleSwMan = null;

	private static String roleProd = null;

	private static String roleAtl = null;

	/* Default values of the proxy, nshosts and lbhosts */
	private static String proxy = "grid02.lal.in2p3.fr";

	private static String nshosts = "node04.datagrid.cea.fr:7772";

	private static String lbhosts = "node04.datagrid.cea.fr:9000";

	/* Default values of pool size and base uid */
	private static String pool_size = "200";

	private static int base_uid = 0;

	/* List containing all the alias names of VO */
	private static List<String> fileAliases = new ArrayList<String>();

	/* List containing all the entire names of VO which are mames with alias */
	private static List<String> VONamesAssociated = new ArrayList<String>();

	// DECLARATION DE METHODES
	
	
	/**
	 * Set the directory for the generated VO templates.
	 * 
	 * @param nameDirTpl
	 *            String containing full path of the directory
	 * 
	 */
	public void setConfigRootDir(String configRootDir) {
		this.configRootDir = configRootDir;
	}

	/**
	 * Set the directory for the generated VO templates.
	 * 
	 * @param nameParamDirTpl
	 *            String containing full path of the directory
	 * 
	 */
	public void setNameParamDirTpl(String nameParamDirTpl) {
		this.nameParamDirTpl = nameParamDirTpl;
	}

	/**
	 * Set the directory for the generated templates containing certificates.
	 * 
	 * @param nameCertDirTpl
	 *            String containing the template form of the path of the directory
	 * 
	 */
	public void setNameCertDirTpl(String nameCertDirTpl) {
		this.nameCertDirTpl = nameCertDirTpl;
	}

	/**
	 * Set the url of the xml file containing data about VOs.
	 * 
	 * @param urlFile
	 *            String containing the template form of the path of the url
	 * 
	 */
	public void setUrlFile(String urlFile) {
		this.urlFile = urlFile;
	}

	/**
	 * Set the directory for the customization templates .
	 * 
	 * @param nameCustomDir
	 *            String containing a template form of the path to the
	 *            customization directory
	 * 
	 */
	public void setNameSiteParamsDir(String nameSiteParamsDir) {
		this.nameSiteParamsDir = nameSiteParamsDir;
	}

	/**
	 * Gets the directory path of the customization templates .
	 * 
	 * @param
	 * 
	 */
	public String getNameSiteParamsDir() {
		String name = nameSiteParamsDir;
		return name;
	}

	/**
	 * Set the directory for the generated templates named with an alias name.
	 * 
	 * @param nameShortNamedDirTpl
	 *            String containing full path to the directory
	 * 
	 */
	public void setNameAliasDirTpl(String nameAliasDirTpl) {
		this.nameAliasDirTpl = nameAliasDirTpl;
	}

	/*
	 * Method used by ant to execute this task.
	 */
	public void execute() throws BuildException {
		// Checking we have enough parameters
		String urlName = urlFile;
		// String fileName = nameFile;

		// On cree une instance de SAXBuilder
		DefaultHandler handler = new MyHandler();
		SAXParserFactory factory = SAXParserFactory.newInstance();

		try {
			URL url = new URL(urlName);
			// File xmlFile = new File(fileName);
			SAXParser saxParser = factory.newSAXParser();
			saxParser.parse(url.openStream(), handler);
			// saxParser.parse(xmlFile, handler);
		} catch (Exception e) {
		}

	}

	/**
	 * Initialize the nlist containing data of the VO.
	 * 
	 * @param VO
	 *            String containing the name of the VO
	 * 
	 */
	/*
	 * public static String initNList(String VO) { String nlist = null; nlist =
	 * "\"" + VO + "\" ?= nlist("; return nlist; }
	 */

	/**
	 * Creates and gets the full path of a generated templates.
	 * 
	 * @param name
	 *            String containing the name of the VO
	 * @param iscert
	 *            Boolean indicating if the generated template contains a
	 *            certificat
	 * @param isAliasNamed
	 *            Boolean indicating the generated templates is for a alias
	 *            named VO
	 * 
	 */
	public static String getFileName(String name, boolean iscert,
			boolean isAliasNamed) {
		String filename = null;
		name = name.toLowerCase();
		String paramDirName = null;
		if (isAliasNamed) {
			paramDirName = configRootDir.concat("/"+nameAliasDirTpl);
		} else {
			paramDirName = configRootDir.concat("/"+nameParamDirTpl);
		}
		filename = name.trim();
		if (iscert) {
			String certDirName = configRootDir.concat("/"+nameCertDirTpl);
			filename = certDirName.concat("/" + filename.concat(".tpl"));
			File dir = new File(certDirName);
			if (!dir.exists() || !dir.isDirectory()) {
				catchError("Directory " + certDirName
						+ " does not exist for VOMS certificates");
			} else {
				for (File file : dir.listFiles()) {
					if ((file.getName()).equals(filename.substring(filename
							.length()))
							&& !file.isDirectory()) {
						String readenCert = "";
						BufferedReader br = null;
						String line;
						try {
							br = new BufferedReader(new FileReader(file));
						} catch (FileNotFoundException e) {
							catchError("File " + file.getAbsolutePath()
									+ " unreadable");
						}
						try {
							int i = 0;
							while ((line = br.readLine()) != null) {
								if ((!line.startsWith("structure"))
										&& (!line.startsWith("\'cert\'"))
										&& (!line.endsWith("EOF"))) {
									readenCert = readenCert.concat(line);
								}
								i++;
							}
							String usedCertificat = certificat.replaceAll("\n",
									"");
							if (readenCert.equals(usedCertificat)) {
								certExists = true;
							} else {
								System.err
										.println("Certificat maybe corrupted for VOMS server "
												+ hostname + "in VO" + VOname);
							}
						} catch (IOException e) {
							e.printStackTrace();
						} finally {
							if (br != null) {
								try {
									br.close();
								} catch (IOException consumed) {
								}
							}
						}
					}
				}
			}
		} else {
			filename = paramDirName.concat("/" + filename.concat(".tpl"));
		}
		return filename;
	}

	/**
	 * Verify the VO is alias named or not and modify the associated template.
	 * 
	 * @param nameVO
	 *            String containing the name of the VO
	 * 
	 */
	public static boolean verifyShortNamedFileExists(String nameVO) {
		boolean result = false;

		VONamesAssociated.add("vo.apc.univ-paris7.fr");
		VONamesAssociated.add("vo.lapp.in2p3.fr");
		VONamesAssociated.add("vo.u-psud.fr");
		VONamesAssociated.add("astro.vo.eu-egee.org");
		VONamesAssociated.add("vo.dapnia.cea.fr");
		VONamesAssociated.add("vo.grif.fr");
		VONamesAssociated.add("vo.ipno.in2p3.fr");
		VONamesAssociated.add("vo.lal.in2p3.fr");
		VONamesAssociated.add("vo.llr.in2p3.fr");
		VONamesAssociated.add("vo.lpnhe.in2p3.fr");
		VONamesAssociated.add("vo.sbg.in2p3.fr");
		VONamesAssociated.add("supernemo.vo.eu-egee.org");
		VONamesAssociated.add("vo.agata.org");

		fileAliases.add(VONamesAssociated.indexOf("vo.apc.univ-paris7.fr"),
				"apc");
		fileAliases.add(VONamesAssociated.indexOf("vo.lapp.in2p3.fr"), "lapp");
		fileAliases.add(VONamesAssociated.indexOf("vo.u-psud.fr"), "psud");
		fileAliases.add(VONamesAssociated.indexOf("astro.vo.eu-egee.org"),
				"astro");
		fileAliases
				.add(VONamesAssociated.indexOf("vo.dapnia.cea.fr"), "dapnia");
		fileAliases.add(VONamesAssociated.indexOf("vo.grif.fr"), "grif");
		fileAliases.add(VONamesAssociated.indexOf("vo.ipno.in2p3.fr"), "ipno");
		fileAliases.add(VONamesAssociated.indexOf("vo.lal.in2p3.fr"), "lal");
		fileAliases.add(VONamesAssociated.indexOf("vo.llr.in2p3.fr"), "llr");
		fileAliases
				.add(VONamesAssociated.indexOf("vo.lpnhe.in2p3.fr"), "lpnhe");
		fileAliases.add(VONamesAssociated.indexOf("vo.sbg.in2p3.fr"), "sbg");
		fileAliases.add(VONamesAssociated.indexOf("supernemo.vo.eu-egee.org"),
				"supernemo");
		fileAliases.add(VONamesAssociated.indexOf("vo.agata.org"), "agata");

		String dirName = configRootDir.concat("/"+nameParamDirTpl);
		File dirTpl = new File(dirName);
		if (!dirTpl.isDirectory()) {
			catchError(dirName + " should be a directory");
		}
		for (String VONameAssociated : VONamesAssociated) {
			if (nameVO.equals(VONameAssociated)) {
				File[] files = dirTpl.listFiles();
				for (File file : files) {
					String fileAlias = fileAliases.get(VONamesAssociated
							.indexOf(nameVO));
					if ((file.getName()).equals(fileAlias.concat(".tpl"))) {
						BufferedWriter bwr = initFile(file.getAbsolutePath());
						write(
								"structure template " + nameParamDirTpl + "/" + fileAlias
										+ ";", bwr);
						write("", bwr);
						write("include " + nameAliasDirTpl + "/" + nameVO + ";", bwr);
						closeFile(file.getName(), bwr);
						result = true;
					}
				}
			}
		}
		return result;
	}

	/**
	 * Creates the account prefix for each VO.
	 * 
	 * @param account
	 *            String containing the name of the VO
	 * @param Id
	 *            int containing the Id of the VO
	 * 
	 */
	public String createAccount(String account, int Id) {
		if (account.startsWith("vo.")) {
			account = account.substring(3, account.length());
		}
		account = account.replaceAll("[^A-Za-z0-9]", "");
		account = account.substring(0, 3);
		account = account.concat(toBase26(Id));
		return account;
	}

	/**
	 * Initiate a BufferedWritter.
	 * 
	 * @param fileName
	 *            String containing the name of the file to write in
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 */
	public static BufferedWriter initFile(String fileName) {
		FileWriter fw = null;
		try {
			fw = new FileWriter(fileName);
		} catch (IOException e) {
			catchError("Could not open file \"" + fileName + "\" for writing ("
					+ e.getMessage() + ")");
			System.exit(-1);
		}
		BufferedWriter bwt = new BufferedWriter(fw);
		return bwt;
	}

	/**
	 * Close a BufferedWritter.
	 * 
	 * @param fileName
	 *            String containing the name of the file
	 * @param bw
	 *            BufferedWriter to close
	 * 
	 */
	public static void closeFile(String fileName, BufferedWriter bw) {
		try {
			bw.flush();
			bw.close();
		} catch (IOException e) {
			System.err.println("Could not close file \"" + fileName + "\" ("
					+ e.getMessage() + ")");
			System.exit(-1);
		}
	}

	/**
	 * Close a BufferedWritter.
	 * 
	 * @param fileName
	 *            String containing the name of the file
	 * @param bw
	 *            BufferedWriter to close
	 * 
	 */
	public static void writeCert() {
		String fileName = getFileName(hostname, true, false);
		if (!certExists) {
			bwCert = initFile(fileName);
			write(
					"structure template " + nameCertDirTpl + "/" + hostname.toLowerCase()
							+ ";", bwCert);
			write("", bwCert);
			write("'cert' = {<<EOF};", bwCert);
			write(certificat, bwCert);
			write("EOF", bwCert);
			closeFile(fileName, bwCert);
		} else {
			certExists = false;
		}
	}

	/**
	 * Generates a base 26 number from an integer.
	 * 
	 * @param i
	 * 
	 */
	public static String toBase26(int i) {
		String s = "";
		s = Integer.toString(i, 26);
		for (int k = 0; k < s.length(); k++) {
			if (s.charAt(k) == '0') {
				s = s.replace('0', 'q');

			}
			if (s.charAt(k) == '1') {
				s = s.replace('1', 'r');

			}
			if (s.charAt(k) == '2') {
				s = s.replace('2', 's');

			}
			if (s.charAt(k) == '3') {
				s = s.replace('3', 't');

			}
			if (s.charAt(k) == '4') {
				s = s.replace('4', 'u');

			}
			if (s.charAt(k) == '5') {
				s = s.replace('5', 'v');

			}
			if (s.charAt(k) == '6') {
				s = s.replace('6', 'w');

			}
			if (s.charAt(k) == '7') {
				s = s.replace('7', 'x');

			}
			if (s.charAt(k) == '8') {
				s = s.replace('8', 'y');

			}
			if (s.charAt(k) == '9') {
				s = s.replace('9', 'z');

			}
		}
		return s.toLowerCase();
	}

	/**
	 * Writes <code>str</code> in BufferedWriter <code>bw</code> (System
	 * exit(-1) is an exception is caught).
	 * 
	 * @param str
	 *            string to write
	 */
	public static void write(String str, BufferedWriter bw) {
		try {
			bw.write(str);
			bw.newLine();
		}

		catch (IOException e) {
			System.err.println("Could not write in file \"Note x\" ("
					+ e.getMessage() + ")");
			System.exit(-1);
		}
	}

	/**
	 * Display the usage message
	 * 
	 * @param error
	 *            String containing the error message
	 */
	public static void catchError(String error) {
		System.err.println("ERROR: " + error);
		System.err.printf("\n");
		System.exit(-1);
	}
}
