package org.quattor.ant;

import java.io.*;
import java.net.URL;

import java.util.LinkedList;
import java.util.regex.Pattern;
import org.apache.tools.ant.types.DirSet;

import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import javax.security.auth.x500.X500Principal;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;

import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
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
	private String proxyHostsFile = null;

	/* the name of the VO */
	private String VOname = null;

	/* the BufferedWriter associated to the VO template */
	private BufferedWriter bw = null;

	/* Default values of the proxy, nshosts and lbhosts */
	private String proxy = "";

	private String nshosts = "";

	private String lbhosts = "";

	private String wms_hosts = "";

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

	// Methods Declaration

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
	 * @param proxyHostsFile
	 *            String containing full path to file
	 * 
	 */
	public void setProxyHostsFile(String proxyHostsFile) {
		this.proxyHostsFile = proxyHostsFile;
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
		String proxyName = configRootDir.concat("/" + proxyHostsFile);
		if ((readFile(proxyName, "proxy").equals(""))
				|| (readFile(proxyName, "proxy").equals(null))) {
			proxy = "undef";
		} else {
			proxy = readFile(proxyName, "proxy");
		}
		if ((readFile(proxyName, "nshosts").equals(""))
				|| (readFile(proxyName, "nshosts").equals(null))) {
			nshosts = "undef";
		} else {
			nshosts = readFile(proxyName, "nshosts");
		}
		if ((readFile(proxyName, "lbhosts").equals(""))
				|| (readFile(proxyName, "lbhosts").equals(null))) {
			lbhosts = "undef";
		} else {
			lbhosts = readFile(proxyName, "lbhosts");
		}
		if ((readFile(proxyName, "wms_hosts").equals(""))
				|| (readFile(proxyName, "wms_hosts").equals(null))) {
			wms_hosts = "undef";
		} else {
			wms_hosts = readFile(proxyName, "wms_hosts");
		}
		// Creation of an instance of SAXBuilder
		String siteParamsFileName = getNameSiteParamsDir();
		DefaultHandler handler = new MyHandler(siteParamsFileName,
				configRootDir, nameParamDirTpl, proxy, nshosts, lbhosts,
				wms_hosts, nameCertDirTpl, nameDNListDirTpl);
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
			String fileN = configRootDir.concat("/"
					+ nameParamDirTpl.concat("/allvos.tpl"));
			BufferedWriter bwavo = initFile(fileN);
			write("unique template " + nameParamDirTpl.concat("/allvos;"),
					bwavo);
			write("", bwavo);
			write("variable ALLVOS ?= list(", bwavo);

			LinkedList<LinkedList<String>> infos = ((MyHandler) handler)
					.getInfos();
			for (LinkedList<String> list : infos) {
				VOname = list.get(1);
				bw = initFile(list.get(2));
				for (int i = 3; i < list.size(); i++) {
					/*
					 * Each LinkedList has the same construction : first element
					 * is the type of template (t = vo template, c = certificat
					 * template, d = dn teplate), the second is the VO name or 1
					 * and the third is the complete path of the file. All the
					 * others contain the datas for a tpl file
					 */
					write(list.get(i), bw);
				}
				closeFile(list.get(2), bw);
				if (list.getFirst().equals("t")) {
					write("    \'" + VOname + "\'" + ",", bwavo);
				}
			}
			write(");", bwavo);
			closeFile(fileN, bwavo);
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
	 * Reads a text file and returns the specified value.
	 * 
	 * @param filepath
	 *            String containing the full path to the file
	 * 
	 * @param element
	 *            String containing the element for which we want the value
	 * 
	 */

	public static String readFile(String filepath, String element) {
		Pattern p = Pattern.compile("=");
		String value = "";
		String[] keyvalue;

		BufferedReader bfrd = null;
		String ligne;
		File file = new File(filepath);
		veriFile(file);
		try {
			bfrd = new BufferedReader(new FileReader(file));

			while ((ligne = bfrd.readLine()) != null) {
				if (!(ligne.equals(""))) {
					keyvalue = p.split(ligne);
					if ((keyvalue[0].trim()).equals(element)) {
						value = keyvalue[1].trim();
					}
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
		return value;
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

		private String VO = null;

		/* the buffer containing data given in the XML document */
		private StringBuffer buffer;

		/* the id of the VO */
		private String VOid = null;

		/* Determines weather or not the certificat exists */
		private String port = null;

		/* the Boolean containing the use of a role */
		private boolean isused = false;

		/* the Lists containing the roles informations */
		private LinkedList<String> fqans;
		
		private LinkedList<String> fqanSufGen;

		/* Default values of pool size and base uid */
		private String pool_size = "200";

		private int base_uid = 0;

		private boolean certExists = false;

		private boolean isOldCert = false;

		private String siteParamsFileName;

		private String root;

		private String nameParamDirTpl;

		private String proxy;

		private String nshosts;

		private String lbhosts;

		private String wms_hosts;

		private String nameCertDirTpl;

		private String nameDNListDirTpl;

		private String fileTplName = null;

		private LinkedList<String> hostList;

		private LinkedList<String> portList;

		private String hostname = null;

		private String certificat = null;

		private String dnPortal = null;

		private String oldCertificat = null;

		private int createCount;

		private LinkedList<LinkedList<String>> allInfos = new LinkedList<LinkedList<String>>();

		private LinkedList<String> dnList = new LinkedList<String>();
		
		/* 
		 * List containing informations for admin, 
		 * production and atlas roles
		 * role:description:suffix
		 */
		LinkedList<String> roleDescrSuf = new LinkedList<String>();


		/*
		 * Constructor.
		 * 
		 */
		public MyHandler(String spfn, String cfrd, String npdt, String prox,
				String nsh, String lbh, String wmsh, String ncdt, String ndnldt) {
			siteParamsFileName = spfn;
			root = cfrd;
			nameParamDirTpl = npdt;
			proxy = prox;
			nshosts = nsh;
			lbhosts = lbh;
			wms_hosts = wmsh;
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
					+ "/voms_dn_list.tpl");
			dnList.add("d");
			dnList.add("0");
			dnList.add(dnlistfilename);
			dnList.add("unique template " + nameDNListDirTpl + "/voms_dn_list;");
			dnList.add("");
			dnList.add("variable VOS_DN_LIST = nlist(");
			
			fqanSufGen = new LinkedList<String>();
			
			roleDescrSuf.add("/Role=lcgadmin,SW manager,s");
			roleDescrSuf.add("/admin,SW manager,s");
			//roleDescrSuf.add("/Role=VO-Admin,SW manager,s");
			roleDescrSuf.add("/Role=VOAdmin,SW manager,s");
			roleDescrSuf.add("/Role=swadmin,SW manager,s");
			roleDescrSuf.add("/Role=sgmadmin,SW manager,s");
			roleDescrSuf.add("/Role=sgm,SW manager,s");
			roleDescrSuf.add("/Role=SoftwareManager,SW manager,s");
			roleDescrSuf.add("/Role=VO-Software-Manager,SW manager,s");
			roleDescrSuf.add("/Role=SW-Admin,SW manager,s");
			roleDescrSuf.add("/Role=production,production,p");
			roleDescrSuf.add("/Role=prod,production,p");
			roleDescrSuf.add("/Role=atlas,ATLAS,atl");

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
				fqans = new LinkedList<String>();
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
				Pattern p = Pattern.compile(",");
				tpl.add("t");
				tpl.add(VO);
				tpl.add(fileTplName);
				String siteParamsFileName = this.siteParamsFileName;
				String siteAliasesFileName = root.concat("/"
						+ siteParamsFileName.concat("/aliases.tpl"));
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
				createCount = 0;
				String accountPrefix = createAccount(VO, Id,
						siteAliasesFileName, true);
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
					if (fqans != null) {
						LinkedList<String> vomsroles = new LinkedList<String>();
						boolean swmexists = false;
						tpl.add("\"voms_roles\" ?= list(");
						for (String fqan : fqans) {
							String[] f = p.split(fqan.trim());
							if (f[1].equals(VO)) {
								String fzero = f[0];
								if (fqan.startsWith("#")) {
									fzero = f[0].substring(1).trim();
								}
								String descr = fzero;
								String fq = fzero;
								String suf = "empty";
								for (String rodesu : roleDescrSuf) {
									String[] rds =  p.split(rodesu.trim());
									if (fzero.equals("/"+VO+rds[0])) {
										descr = rds[1];
										suf = rds[2];
									}
								}
								if (suf.equals("empty")) {
									String sufgen = generIdent(fzero, fzero.length(), Integer.parseInt(VOid), fqanSufGen);
									fqanSufGen.add(sufgen +"," + VO);
									suf = p.split(sufgen.trim())[1];
								}
								
								/*traitement de VO-Admin*/
								if (suf.equals("s")){
									swmexists = true;
								}
								if (fqan.startsWith("#")) {
									vomsroles.add(descr+","+fq+","+suf+",#");
									/*tpl.add("#    nlist(\"description\", \""+ descr + "\",");
									tpl.add("#      \"fqan\", \"" + fq + "\",");
									tpl.add("#      \"suffix\", \"" + suf + "\"),");*/
								} else {
									if (suf.equals("s")){
										vomsroles.addFirst(descr+","+fq+","+suf+", ");
									}else if (suf.equals("p")){
										if (!swmexists){
											vomsroles.addFirst(descr+","+fq+","+suf+", ");
										} else {
											vomsroles.add(1, descr+","+fq+","+suf+", ");
										}
									} else {
										vomsroles.add(descr+","+fq+","+suf+", ");
									}
									/*tpl.add("     nlist(\"description\", \""+ descr + "\",");
									tpl.add("       \"fqan\", \"" + fq + "\",");
									tpl.add("       \"suffix\", \"" + suf + "\"),");*/
								}
							}
						}
						for (String vomsrole : vomsroles){
							String[] vr = p.split(vomsrole);
							String desc = vr[0];
							String fqa = vr[1];
							String su = vr[2];
							if ((vr[0].equals("/"+VO+"/Role=VO-Admin")) && (!swmexists)) {
								desc = "SW manager";
								su = "s";
							}
							if (vomsrole.endsWith("#")) {
								tpl.add("#    nlist(\"description\", \""+ desc + "\",");
								tpl.add("#      \"fqan\", \"" + fqa + "\",");
								tpl.add("#      \"suffix\", \"" + su + "\"),");
							} else {
								tpl.add("     nlist(\"description\", \""+ desc + "\",");
								tpl.add("       \"fqan\", \"" + fqa + "\",");
								tpl.add("       \"suffix\", \"" + su + "\"),");									
							}
						}
					}
					tpl.add("     );");
					tpl.add("");
				}
				tpl.add("\"proxy\" ?= '" + proxy + "';");
				tpl.add("\"nshosts\" ?= '" + nshosts + "';");
				tpl.add("\"lbhosts\" ?= '" + lbhosts + "';");
				tpl.add("");
				if (wms_hosts.equals("undef")) {
					tpl.add("\"wms_hosts\" ?= " + wms_hosts + ";");
				} else {
					String[] wmshs = wms_hosts.split(",");
					int wm = 0;
					for (String wmsh : wmshs) {
						if (wm == 0) {
							tpl.add("\"wms_hosts\" ?= list('" + wmsh.trim()
									+ "',");
							wm++;
						} else {
							tpl.add("                    '" + wmsh.trim()
									+ "',");
						}
					}
					tpl.add("                   );");
				}
				tpl.add("");
				tpl.add("\"pool_size\" ?= " + pool_size + ";");
				base_uid = Id * 1000;
				tpl.add("\"base_uid\" ?= " + base_uid + ";");
				// tpl.add(");");
				hostname = null;
				port = null;
				certificat = null;
				dnPortal = null;
				allInfos.add(tpl);
			} else if (qualifiedName.equals("IS_GROUP_USED")) {
				if (buffer.toString().equals("1")) {
					isused = true;
				} else {
					isused = false;
				}
			} else if (qualifiedName.equals("GROUP_ROLE")) {
				String fqan = buffer.toString().trim();
				if (!(fqan.equals("/"+VO+"/Role=NULL")) && (!(fqan.equals(null))) && (!(fqan.equals("/"+VO)))){
					if ((fqan.endsWith("/Role=NULL"))) {
						fqan = fqan.replaceAll("/Role=NULL", "/");
					}
					if (isused) {
						fqans.add(fqan + "," + VO);
					} else {
						fqans.add("#" + fqan + "," + VO);
					}
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
				buffer = null;
			} else if ((qualifiedName.equals("DN"))
					&& (!buffer.toString().equals(""))) {
				dnPortal = buffer.toString().trim();
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
					createCert();
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
			if (!checkDNCert(certificat)) {
				System.err.println("Wrong DN of certificat for VO " + VO);
			} else if (!checkValidityCert(certificat)) {
				System.err.println("Certificat not valid for VO " + VO);
			} else if (!(certificat.startsWith(beginTag))) {
				System.err.println("Wrong form of certificat for VO " + VO);
			} else if (!certExists) {
				LinkedList<String> certtpl = new LinkedList<String>();
				certtpl.add("c");
				certtpl.add(VO);
				certtpl.add(fileName);
				certtpl.add("structure template " + nameCertDirTpl + "/"
						+ hostname.toLowerCase() + ";");
				certtpl.add("");
				certtpl.add("'cert' = {<<EOF};");
				certtpl.add(certificat);
				certtpl.add("EOF");
				if (isOldCert) {
					certtpl.add("");
					certtpl.add("'oldcert' = {<<EOF};");
					certtpl.add(oldCertificat);
					certtpl.add("EOF");
					isOldCert = false;
				}
				allInfos.add(certtpl);
			} else {
				certExists = false;
			}
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
					String dnsubiss = "       \"" + hostname
							+ "\", nlist(\"subject\", \"" + subject.toString()
							+ "\",\n                  \"issuer\", \""
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
		 * Check the DN of a certificat.
		 * 
		 * @param certif
		 *            String containing the certificat
		 */
		public boolean checkDNCert(String certif) {
			boolean isValid = false;
			X509Certificate cert = null;
			try {
				cert = extractCertificates(certificat);
				if (cert != null) {
					X500Principal subject = cert.getSubjectX500Principal();
					// System.out.println("DN : "+subject.toString());
					// System.out.println("DN CIC : "+dnPortal);
					String[] terms = subject.toString().split(",");
					String dninvert = "";
					for (String term : terms) {
						dninvert = "/" + (term.trim()).concat(dninvert);

					}
					// System.out.println("DN inverse: "+dninvert);
					if (dninvert.equals(dnPortal.trim())) {
						isValid = true;
					}
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return isValid;
		}

		/**
		 * Check the validity of a certificat.
		 * 
		 * @param certif
		 *            String containing the certificat
		 * @throws CertificateNotYetValidException
		 * @throws CertificateExpiredException
		 */
		public boolean checkValidityCert(String certif) {
			boolean isValid = false;
			X509Certificate cert = null;
			try {
				cert = extractCertificates(certificat);
				cert.checkValidity();
				isValid = true;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (CertificateExpiredException e) {
				System.err.println(e);
			} catch (CertificateNotYetValidException e) {
				System.err.println(e);
			}
			return isValid;
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

			// name for a certificat file
			if (iscert) {
				String certDirName = root.concat("/" + nameCertDirTpl);
				filename = certDirName.concat("/" + filename.concat(".tpl"));
				File dir = new File(certDirName);
				if (!dir.exists() || !dir.isDirectory()) {
					catchError("Directory " + certDirName
							+ " does not exist for VOMS certificates");
				} else {
					// does the file exist?
					for (File file : dir.listFiles()) {
						if ((file.getName()).equals(filename
								.substring(certDirName.length() + 1))
								&& !file.isDirectory()) {
							String readenCert = "";
							String readenOldCert = "";
							String cert = "";
							BufferedReader br = null;
							String line;
							try {
								br = new BufferedReader(new FileReader(file));
							} catch (FileNotFoundException e) {
								catchError("File " + file.getAbsolutePath()
										+ " unreadable");
							}
							try {
								boolean inoldcert = false;
								while ((line = br.readLine()) != null) {
									if (!inoldcert) {
										if ((!line.startsWith("structure"))
												&& (!line
														.startsWith("\'cert\'"))
												&& (!line.endsWith("EOF"))) {

											if (!line.startsWith("\'oldcert\'")) {
												readenCert = readenCert
														.concat(line.trim());
												cert = cert.concat(line)
														.concat("\n");
											} else {
												inoldcert = true;
											}
										}
									} else {
										if ((!line.endsWith("EOF"))) {
											readenOldCert = readenOldCert
													.concat(line.trim());
										}
									}
								}
								String usedCertificat = certificat.replaceAll(
										"\n", "");

								// the file already exists with the same key
								if (readenCert.equals(usedCertificat)) {
									certExists = true;
								} else if (readenOldCert.equals(usedCertificat)) {
									// the file already exists with the old key
									System.err
											.println("Certificat warning : Key is not up to date for VO "
													+ VO);
									certExists = true;
								} else {
									// there's a new key and we have to create
									// the oldcert entry
									isOldCert = true;
									oldCertificat = cert;
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
		public String createAccount(String account, int Id,
				String siteAliasesFileName, boolean isfirst) {
			if (account.startsWith("vo.")) {
				account = account.substring(3, account.length());
			}
			account = account.replaceAll("[^A-Za-z0-9]", "");
			account = account.substring(0, 3);
			if (!isfirst) {
				createCount = createCount + 1;
				account = account.concat(toBase26(createCount));
			}
			account = account.concat(toBase26(Id));
			if (!verifAccount(account, siteAliasesFileName)) {
				System.out
						.println("############################################");
				System.out.println("account_prefix " + account
						+ " is already used");
				System.out.println("creation of a new account_prefix for VO "
						+ VO);
				System.out
						.println("############################################");
				account = createAccount(account, Id, siteAliasesFileName, false);
			}
			return account;
		}

		/**
		 * Verify that the created account does not already exists.
		 * 
		 * @param account
		 *            String containing the account
		 * @param siteAliasesFileName
		 *            String containing the path to the aliases file
		 * 
		 */
		public boolean verifAccount(String account, String siteAliasesFileName) {
			boolean accountok = true;
			BufferedReader bfrd = null;
			String ligne;
			Pattern p = Pattern.compile(",");
			String[] keyvalue;
			LinkedList<String> aliases = new LinkedList<String>();
			File file = new File(siteAliasesFileName);
			if (!file.exists() || !file.isFile()) {
				throw new BuildException("can't open " + file.getName()
						+ ": No such file or directory");
			}
			try {
				bfrd = new BufferedReader(new FileReader(file));
				while ((ligne = bfrd.readLine()) != null) {
					if ((ligne.trim()).startsWith("'")) {
						keyvalue = p.split(ligne.trim());
						String alias = (keyvalue[0].trim());
						aliases.add(alias.substring(1, alias.length() - 1));
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
			for (String al : aliases) {
				if (account.equals(al)) {
					accountok = false;
				}
			}
			return accountok;
		}

		/**
		 * Generates a suffix for a given role.
		 * 
		 * @param st
		 *            String containing the role
		 * @param i,
		 *            idt int for generate the suffix
		 * @param list
		 *            List containing a String [role,suffix]
		 */
		public String generIdent(String st, int i, int idt,
				LinkedList<String> list) {
			String id = toBase26(i) + toBase26(idt);
			String idtGen = st + "," + id + "," + VO;
			Pattern p = Pattern.compile(",");
			boolean exist = false;
			if (list != null){
				for (String roleinfo : list) {
					String[] role = p.split(roleinfo.trim());
					if (role[1].equals(id) && (!(role[0].equals(st)))) {
						exist = true;
					}
				}
			}
			if (exist) {
				idtGen = generIdent(st, i+100, idt, list);
			}
				
		return idtGen;
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
