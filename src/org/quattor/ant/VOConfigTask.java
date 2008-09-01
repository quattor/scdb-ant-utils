package org.quattor.ant;

import java.io.*;
import java.net.URL;

import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.tools.ant.types.DirSet;

import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import javax.security.auth.x500.X500Principal;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;

import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;

/**
 * This task creates pan templates from data given by the CIC portal
 * 
 * @author duprilot
 */
public class VOConfigTask extends Task {

	// DECLARATION DE VARIABLES

	/* the name of the root directory */
	private DirSet configDirs = null;

	/* the name of the root directory */
	private String configRootDir = null;

	/* the name of the directory containing generated templates */
	private String nameParamDirTpl = null;

	/* the name of the directory containing generated certificates templates */
	private String nameDNListDirTpl = null;

	/* the name of the directory containing generated certificates templates */
	private String nameCertDirTpl = null;

	/* the name of the file containing VOs informations */
	private String urlFile = null;

	/* the name of the url where to find the XML document */
	private String inputFile = null;

	/* the name of the directory containing customization templates */
	private String nameSiteParamsDir = null;

	/* the name of the file containing proxy */
	private String proxyFile = null;

	/* the name of the VO */
	private String VOname = null;

	/* the BufferedWriter associated to the VO template */
	private BufferedWriter bw = null;

	/* Default values of the proxy, nshosts and lbhosts */
	private String proxy = "";

	final public static CertificateFactory cf;
	static {
		try {
			cf = CertificateFactory.getInstance("X.509");
		} catch (CertificateException ce) {
			throw new BuildException(ce.getMessage());
		}
	}

	final public static String beginTag = "-----BEGIN CERTIFICATE-----";

	final public static String endTag = "-----END CERTIFICATE-----";

	// DECLARATION DE METHODES

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
	 *            String containing the template form of the path of the
	 *            directory
	 * 
	 */
	public void setNameDNListDirTpl(String nameDNListDirTpl) {
		this.nameDNListDirTpl = nameDNListDirTpl;
	}

	/**
	 * Set the directory for the generated templates containing certificates.
	 * 
	 * @param nameCertDirTpl
	 *            String containing the template form of the path of the
	 *            directory
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
	 * Set the xml file containing data about VOs.
	 * 
	 * @param inputFile
	 *            String containing the path to the file
	 * 
	 */
	public void setInputFile(String inputFile) {
		this.inputFile = inputFile;
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
	 * Set the file containing the proxy name.
	 * 
	 * @param proxyFile
	 *            String containing full path to file
	 * 
	 */
	public void setProxyFile(String proxyFile) {
		this.proxyFile = proxyFile;
	}

	/*
	 * Method used by ant to execute this task.
	 */
	@Override
	public void execute() throws BuildException {
		DirectoryScanner ds = configDirs.getDirectoryScanner(getProject());
		// Loop over each file creating a File object.
		File basedir = ds.getBasedir();
		for (String f : ds.getIncludedDirectories()) {
			executeBranch(new File(basedir, f).getAbsolutePath());
		}
	}

	public void executeBranch(String dir) throws BuildException {
		configRootDir = dir;
		// Checking we have enough parameters
		String urlName = urlFile;
		String proxyName = configRootDir.concat("/" + proxyFile);
		proxy = readFile(proxyName);
		// On cree une instance de SAXBuilder
		String siteParamsFileName = getNameSiteParamsDir();
		DefaultHandler handler = new MyHandler(siteParamsFileName,
				configRootDir, nameParamDirTpl, proxy, nameCertDirTpl,
				nameDNListDirTpl);
		SAXParserFactory factory = SAXParserFactory.newInstance();
		try {
			URL url = new URL(urlName);
			File xmlFile = new File(inputFile);
			SAXParser saxParser = factory.newSAXParser();
			if (xmlFile.exists()) {
				System.out.println("Reading File : "
						+ xmlFile.getAbsolutePath());
				System.out.println("Document parsing and templates creation");
				saxParser.parse(xmlFile, handler);
			} else {
				System.out
						.println("Creation of the flow to CIC portal (may take up till one minute)");
				InputStream urlstream = url.openStream();
				System.out.println("Document parsing and templates creation");
				saxParser.parse(urlstream, handler);
			}

			LinkedList<LinkedList<String>> infos = ((MyHandler) handler).getInfos();
			for (LinkedList<String> list : infos) {
				VOname = list.getFirst();
				bw = initFile(list.get(1));
				for (int i = 2; i < list.size(); i++) {
					/*
					 * Each LinkedList has the same construction : first element
					 * is the VO name or 1 second the complete path of the file
					 * all the datas for a tpl file
					 */
					write(list.get(i), bw);
					// System.out.println("s : "+s);
				}
				closeFile(list.get(1), bw);
			}
			System.out.println("Templates created for " + configRootDir + "\n");
		} catch (Exception e) {
			System.err
					.println("\n--\nBAD XML FORMAT - Contact CIC operations portal for more informations\n--\n");
			System.out.println("Templates generation for VO " + VOname
					+ " and followers failed\n--\n");
			System.out.println("All VO Identity Card can be found at: \n");
			System.out
					.println("https://cic.gridops.org/downloadRP.php?section=lavoisier&rpname=vocard&vo=all\n--\n");

			throw new BuildException("BUILD FAILED : " + e.getMessage());
		}
	}

	/**
	 * Initializes the nlist containing data of the VO.
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
	 * Reads a text file and returns the first line.
	 * 
	 * @param filepath
	 *            String containing the full path to the file
	 * 
	 */

	public static String readFile(String filepath) {
		LinkedList<String> objects = new LinkedList<String>();
		BufferedReader bfrd = null;
		String ligne;
		File file = new File(filepath);
		veriFile(file);
		try {
			bfrd = new BufferedReader(new FileReader(file));

			while ((ligne = bfrd.readLine()) != null) {
				if (!(ligne.equals(""))) {
					objects.add(ligne);
				}
			}
		} catch (FileNotFoundException exc) {
			System.out.println("File " + file.getName() + " Opening Error");
			throw new BuildException(exc.getMessage());
		} catch (IOException e) {
			System.out.println("Reading " + file.getName() + " Error");
			throw new BuildException(e.getMessage());
		} finally {
			try {
				if (bfrd != null) {
					bfrd.close();
				}
			} catch (IOException e) {
				System.out.println("Closing " + file.getName() + " Error");
				throw new BuildException(e.getMessage());
			}
		}
		return objects.getFirst();
	}

	/**
	 * Initiate a BufferedWritter.
	 * 
	 * @param fileName
	 *            String containing the name of the file to write in
	 * 
	 * 
	 */
	public static BufferedWriter initFile(String fileName) {
		FileWriter fw = null;
		try {
			fw = new FileWriter(fileName);
		} catch (IOException e) {
			throw new BuildException("Could not open file \"" + fileName
					+ "\" for writing (" + e.getMessage() + ")");
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
			throw new BuildException("Could not close file \"" + fileName
					+ "\" (" + e.getMessage() + ")");
		}
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
			throw new BuildException("Could not write in file \"Note x\" ("
					+ e.getMessage() + ")");
		}
	}

	/**
	 * Display the usage message
	 * 
	 * @param error
	 *            String containing the error message)
	 */
	public static void catchError(String error) {
		System.err.println("ERROR: " + error);
		System.err.printf("\n");
	}

	/**
	 * Verify if a file exists
	 * 
	 * @param file
	 *            the file
	 */
	public static void veriFile(File file) {
		if (!file.exists() || !file.isFile()) {
			throw new BuildException("can't open " + file.getName()
					+ ": No such file or directory");
		}
	}

	/**
	 * This handler parse the xml file given by the CIC portal
	 * 
	 * @author duprilot
	 */
	public static class MyHandler extends DefaultHandler {

		/* the buffer containing data given in the XML document */
		private StringBuffer buffer;

		/* the patterns used to collect the roles */
		private final Pattern padmin = Pattern.compile("Role=lcgadmin",
				Pattern.CASE_INSENSITIVE);

		private final Pattern padmin2 = Pattern.compile("/admin",
				Pattern.CASE_INSENSITIVE);

		private final Pattern padmin3 = Pattern.compile("Role=VO-Admin",
				Pattern.CASE_INSENSITIVE);

		private final Pattern padmin4 = Pattern.compile("Role=VOAdmin",
				Pattern.CASE_INSENSITIVE);

		private final Pattern pswman = Pattern.compile("Role=SoftwareManager",
				Pattern.CASE_INSENSITIVE);

		private final Pattern pswadmin = Pattern.compile("Role=swadmin",
				Pattern.CASE_INSENSITIVE);

		private final Pattern pprod = Pattern.compile("Role=production",
				Pattern.CASE_INSENSITIVE);

		private final Pattern patlas = Pattern.compile("Role=atlas",
				Pattern.CASE_INSENSITIVE);

		/* the id of the VO */
		private String VOid = null;

		/* Determines weather or not the certificat exists */
		private String port = null;

		/* the Strings containing the roles */
		private String roleAdmin = null;

		private String roleSwAdmin = null;

		private String roleSwMan = null;

		private String roleProd = null;

		private String roleAtl = null;

		private final static String nshosts = "node04.datagrid.cea.fr:7772";

		private final static String lbhosts = "node04.datagrid.cea.fr:9000";

		/* Default values of pool size and base uid */
		private String pool_size = "200";

		private int base_uid = 0;

		/* the BufferedWriter associated to the certificat template */
		private boolean certExists = false;

		private String siteParamsFileName;

		private String root;

		private String nameParamDirTpl;

		private String proxy;

		private String nameCertDirTpl;

		private String nameDNListDirTpl;

		private String VO = null;

		private String fileTplName = null;

		private LinkedList<String> hostList;

		private LinkedList<String> portList;

		private String hostname = null;

		private String certificat = null;

		LinkedList<LinkedList<String>> allInfos = new LinkedList<LinkedList<String>>();

		LinkedList<String> dnList = new LinkedList<String>();

		/*
		 * Constructor.
		 * 
		 */
		public MyHandler(String spfn, String cfrd, String npdt, String prox,
				String ncdt, String ndnldt) {
			siteParamsFileName = spfn;
			root = cfrd;
			nameParamDirTpl = npdt;
			proxy = prox;
			nameCertDirTpl = ncdt;
			nameDNListDirTpl = ndnldt;
		}

		/*
		 * Error thrower.
		 * 
		 */
		@Override
		public void error(SAXParseException e) throws SAXParseException {
			BuildException be = new BuildException(e.getLocalizedMessage());
			throw be;
		}

		/*
		 * The document is in parsing.
		 * 
		 */
		@Override
		public void startDocument() throws SAXException {
			String dnlistfilename = root.concat("/" + nameDNListDirTpl
					+ "/vos_dn_list.tpl");
			dnList.add("0");
			dnList.add(dnlistfilename);
			dnList.add("unique template " + nameDNListDirTpl + "/vos_dn_list;");
			dnList.add("");
			dnList.add("variable VOS_DN_LIST = nlist(");

		}

		/*
		 * The parsing of the document is finished.
		 * 
		 */
		@Override
		public void endDocument() throws SAXException {
			dnList.add("       );");
			allInfos.add(dnList);
		}

		/*
		 * Opening of an element.
		 * 
		 */
		@Override
		public void startElement(String namespaceURI, String simpleName,
				String qualifiedName, Attributes attrs) throws SAXException {
			if (qualifiedName.equals("VO")) {
				VO = attrs.getValue("Name");
				VO = VO.toLowerCase();
				VOid = attrs.getValue("ID");
				fileTplName = getFileName(VO, false, false);
				hostList = new LinkedList<String>();
				portList = new LinkedList<String>();
			} else {
				buffer = new StringBuffer();
			}
		}

		/*
		 * The element is closed.
		 * 
		 */
		@Override
		public void endElement(String namespaceURI, String simpleName,
				String qualifiedName) throws SAXException {
			if (qualifiedName.equals("VO")) {
				LinkedList<String> tpl = new LinkedList<String>();
				tpl.add(VO);
				tpl.add(fileTplName);
				String siteParamsFileName = this.siteParamsFileName;
				String siteParamName = VO;
				siteParamsFileName = siteParamsFileName.concat("/").concat(
						siteParamName);
				tpl.add("structure template " + nameParamDirTpl + "/" + VO
						+ ";");
				tpl.add("");
				tpl.add("include {if_exists('" + siteParamsFileName + "')};");
				tpl.add("");
				// String nList = initNList(VO);
				int Id = Integer.parseInt(VOid);
				String accountPrefix = createAccount(VO, Id);
				// write(nList, bw);
				tpl.add("\"name\" ?= '" + VO + "';");
				tpl.add("\"account_prefix\" ?= '" + accountPrefix + "';");
				tpl.add("");
				if ((hostname != null) && (port != null)) {
					if (hostList.size() > 1) {
						tpl.add("\"voms_servers\" ?= list(");
						for (int i = 0; i < hostList.size(); i++) {
							tpl.add("      nlist(\"name\", '" + hostList.get(i)
									+ "',");
							tpl.add("            \"host\", '" + hostList.get(i)
									+ "',");
							tpl.add("            \"port\", " + portList.get(i)
									+ ",");
							tpl.add("            ),");
						}
						tpl.add("      );");
						tpl.add("");
					} else {
						tpl.add("\"voms_servers\" ?= nlist(\"name\", '"
								+ hostname + "',");
						tpl.add("      \"host\", '" + hostname + "',");
						tpl.add("      \"port\", " + port + ",");
						tpl.add("      );");
						tpl.add("");
					}
					if ((roleAdmin != null) || (roleProd != null)
							|| (roleAtl != null) || (roleSwAdmin != null)
							|| (roleSwMan != null)) {
						tpl.add("\"voms_roles\" ?= list(");
						if (roleAdmin != null) {
							tpl.add("     nlist(\"description\", \"SW manager\",");
							tpl.add("       \"fqan\", \"lcgadmin\",");
							tpl.add("       \"suffix\", \"s\"),");
						}
						if (roleSwAdmin != null) {
							tpl.add("     nlist(\"description\", \"SW manager\",");
							tpl.add("       \"fqan\", \"swadmin\",");
							tpl.add("       \"suffix\", \"s\"),");
						}
						if (roleProd != null) {
							tpl.add("     nlist(\"description\", \"production\",");
							tpl.add("       \"fqan\", \"production\",");
							tpl.add("       \"suffix\", \"p\"),");
						}
						if (roleAtl != null) {
							tpl.add("     nlist(\"description\", \"ATLAS\",");
							tpl.add("       \"fqan\", \"atlas\",");
							tpl.add("       \"suffix\", \"atl\"),");
						}
						if (roleSwMan != null) {
							tpl.add("     nlist(\"description\", \"SW manager\",");
							tpl.add("       \"fqan\", \"SoftwareManager\",");
							tpl.add("       \"suffix\", \"s\"),");
						}
						tpl.add("     );");
						roleProd = null;
						roleAdmin = null;
						roleAtl = null;
						roleSwAdmin = null;
						roleSwMan = null;
					}
					tpl.add("");
				}
				tpl.add("\"proxy\" ?= '" + proxy + "';");
				tpl.add("\"nshosts\" ?= '" + nshosts + "';");
				tpl.add("\"lbhosts\" ?= '" + lbhosts + "';");
				tpl.add("");
				tpl.add("\"pool_size\" ?= " + pool_size + ";");
				base_uid = Id * 1000;
				tpl.add("\"base_uid\" ?= " + base_uid + ";");
				// tpl.add(");");
				hostname = null;
				port = null;
				certificat = null;
				allInfos.add(tpl);
			} else if (qualifiedName.equals("GROUP_ROLE")) {
				Matcher m = padmin.matcher(buffer.toString());
				Matcher mbis = padmin2.matcher(buffer.toString());
				Matcher mter = padmin3.matcher(buffer.toString());
				Matcher mqua = padmin4.matcher(buffer.toString());
				Matcher m2 = pprod.matcher(buffer.toString());
				Matcher m3 = patlas.matcher(buffer.toString());
				Matcher m4 = pswadmin.matcher(buffer.toString());
				Matcher m5 = pswman.matcher(buffer.toString());
				if ((m.find()) || (mbis.find()) || (mter.find())
						|| (mqua.find())) {
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
				certificat = buffer.toString().trim();
				createCert();
				buffer = null;
			} else if (qualifiedName.equals("VOMSServer")) {
				if ((hostname == null) || (certificat == null)) {
					System.err
							.println("Problem while creation of lsc file for VO "
									+ VO + ": no hostname or certificat");
				} else if (!createLscFile(hostname, certificat, VO)) {
					System.err
							.println("Problem while creation of lsc file for VO "
									+ VO + " with hostname " + hostname);
				} else {
					hostList.add(hostname);
					portList.add(port);
				}
			} else if (qualifiedName.equals("VOMSServers")) {
				if (hostname == null) {
					System.err
							.println("VOMSServer attribute doesn't exist for VO "
									+ VO);
				}
			}
		}

		@Override
		public void characters(char buf[], int offset, int len)
				throws SAXException {
			String s = new String(buf, offset, len);
			if (buffer != null) {
				buffer.append(s);
			}
		}

		/**
		 * Create a certificat file.
		 * 
		 */
		public void createCert() {
			String fileName = getFileName(hostname, true, false);

			if (!certExists) {
				LinkedList<String> certtpl = new LinkedList<String>();
				certtpl.add(VO);
				certtpl.add(fileName);
				certtpl.add("structure template " + nameCertDirTpl + "/"
						+ hostname.toLowerCase() + ";");
				certtpl.add("");
				certtpl.add("'cert' = {<<EOF};");
				certtpl.add(certificat);
				certtpl.add("EOF");
				allInfos.add(certtpl);
			} else {
				certExists = false;
			}
		}

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
		public String getFileName(String name, boolean iscert,
				boolean isAliasNamed) {
			String filename = null;
			name = name.toLowerCase();
			String paramDirName = null;
			paramDirName = root.concat("/" + nameParamDirTpl);
			filename = name.trim();
			if (iscert) {
				String certDirName = root.concat("/" + nameCertDirTpl);
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
								String usedCertificat = certificat.replaceAll(
										"\n", "");
								if (readenCert.equals(usedCertificat)) {
									certExists = true;
								} else {
									System.err
											.println("Certificat maybe corrupted for VOMS server "
													+ hostname + "in VO" + VO);
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
		 * Creates the lsc file containing the DN of the voms server for each
		 * VO.
		 * 
		 * @param hostname
		 *            String containing the name of the hostname
		 * @param certificat
		 *            String containing the certificat
		 * @param VOname
		 *            String containing the name of the VO
		 * 
		 */
		public boolean createLscFile(String hostname, String certificat,
				String VOname) {
			boolean result = false;
			boolean dnexist = false;
			X509Certificate c = null;
			try {
				c = extractCertificates(certificat);

				if (c != null) {
					X500Principal subject = c.getSubjectX500Principal();
					X500Principal issuer = c.getIssuerX500Principal();
					String dnsubiss = "       \"" + hostname + "\", list(\""
							+ subject.toString() + "\",\n             \""
							+ issuer.toString() + "\"),\n";

					for (String dn : dnList) {
						if (dnsubiss.equals(dn)) {
							dnexist = true;
						}
					}
					if (!dnexist) {
						dnList.add(dnsubiss);
					}
					result = true;
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return result;
		}

		/**
		 * This method will extract a list of X509 certificates from a file.
		 * Unfortunately, the native Java routines are not tolerant of
		 * extraneous information in the file, so we must string out that
		 * information manually. This causes lots of gymnastics for a relatively
		 * simple task.
		 * 
		 * @param file
		 * 
		 * @return array of X509Certificates from the file
		 * 
		 * @throws IOException
		 */
		private X509Certificate extractCertificates(String certif)
				throws IOException {

			X509Certificate cert = null;

			if (!(certif.startsWith(beginTag))) {
				System.err.println("Wrong form of certificat for VO " + VO);
			}

			else {
				// Store the real information in memory (in a byte
				// array).
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				OutputStreamWriter osw = new OutputStreamWriter(baos);
				// Process the file line by line saving only
				// information between certificate markers (including
				// the markers themselves).
				osw.write(certif);
				osw.write("\n");
				// Convert the buffer to a byte array and create an
				// InputStream to read from it.
				osw.close();
				byte[] certInfo = baos.toByteArray();
				ByteArrayInputStream bais = new ByteArrayInputStream(certInfo);
				// Now actually process the embedded certificates.
				// Lots of gymnastics for doing something simple.
				while (bais.available() > 0) {
					try {
						cert = (X509Certificate) cf.generateCertificate(bais);
					} catch (CertificateException ce) {
						throw new BuildException(ce.getMessage());
					}
				}
			}
			return cert;
		}

		/**
		 * Generates a base 26 number from an integer.
		 * 
		 * @param i
		 * 
		 */
		public static String toBase26(int i) {
			StringBuilder sb = new StringBuilder(Integer.toString(i, 26));

			for (int k = 0; k < sb.length(); k++) {
				if (sb.charAt(k) == '0') {
					sb.setCharAt(k, 'q');
				}
				if (sb.charAt(k) == '1') {
					sb.setCharAt(k, 'r');
				}
				if (sb.charAt(k) == '2') {
					sb.setCharAt(k, 's');
				}
				if (sb.charAt(k) == '3') {
					sb.setCharAt(k, 't');
				}
				if (sb.charAt(k) == '4') {
					sb.setCharAt(k, 'u');
				}
				if (sb.charAt(k) == '5') {
					sb.setCharAt(k, 'v');
				}
				if (sb.charAt(k) == '6') {
					sb.setCharAt(k, 'w');
				}
				if (sb.charAt(k) == '7') {
					sb.setCharAt(k, 'x');
				}
				if (sb.charAt(k) == '8') {
					sb.setCharAt(k, 'y');
				}
				if (sb.charAt(k) == '9') {
					sb.setCharAt(k, 'z');
				}
			}
			return (sb.toString()).toLowerCase();
		}

		/**
		 * Create a certificat file.
		 * 
		 */
		public LinkedList<LinkedList<String>> getInfos() {
			return allInfos;
		}
	}
}
