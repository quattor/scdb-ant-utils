package org.quattor.ant;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import javax.security.auth.x500.X500Principal;

public class CertChainExample {

	final public static String beginTag = "-----BEGIN CERTIFICATE-----";
	final public static String endTag = "-----END CERTIFICATE-----";

	// Create a certificate factory to generate java representations of the X509
	// certificates.
	final public static CertificateFactory cf;
	static {
		try {
			cf = CertificateFactory.getInstance("X.509");
		} catch (CertificateException ce) {
			throw new RuntimeException(ce.getMessage());
		}
	}

	public static void main(String[] args) {

		// Do some argument checking. First argument must be the directory with
		// all of the trusted certificates. Second must be a certificate file.
		if (args.length < 2) {
			usage();
		}

		// The directory to search for CA certificates.
		File directory = new File(args[0]);
		if (!directory.isDirectory()) {
			usage();
		}

		// The certificate file to get the chain for.
		File targetCert = new File(args[1]);
		if (!targetCert.canRead()) {
			usage();
		}

		// Create a filter that accepts only files named *.0.
		FilenameFilter filter = new CertFilter();

		// Create a holder for the certificate information.
		ChainHolder chains = new ChainHolder();

		try {

			// Loop over each file and process the embedded certificate.
			for (String fname : directory.list(filter)) {

				File file = new File(directory, fname);
				System.out.println("FILE: " + file.toString());

				for (X509Certificate c : extractCertificates(file)) {
					chains.add(c.getSubjectX500Principal(), c
							.getIssuerX500Principal());
				}
			}

			// Extract the subject from the target certificate and print the
			// chain(s).
			for (X509Certificate c : extractCertificates(targetCert)) {
				X500Principal subject = c.getSubjectX500Principal();
				X500Principal issuer = c.getIssuerX500Principal();
				System.out.println("-----");
				for (String s : chains.getChain(subject, issuer)) {
					System.out.println(s);
				}
				System.out.println("-----");
			}

		} catch (FileNotFoundException fnfe) {
			fnfe.printStackTrace();
			System.exit(1);
		} catch (IOException ioe) {
			ioe.printStackTrace();
			System.exit(1);
		}

	}

	private static void usage() {
		System.err.println("java CertChainExample CAdir certFile");
		System.exit(1);
	}

	/**
	 * This method converts an X500Principal into a subject DN with the globus
	 * format. The algorithm used here is really a hack. It uses the
	 * undocumented format from the toString() method and may not handle all
	 * escaped characters corrected. Use with care!
	 * 
	 * @param subject
	 * 
	 * @return globus formatted subject DN
	 */
	private static String convertDN(X500Principal subject) {

		// Extract the subject DN and split this into term around comma
		// separators.
		String dn = subject.toString();
		String[] terms = dn.split(",\\s*");

		// Build up the globus string by iterating through the terms backwards.
		StringBuilder sb = new StringBuilder();
		for (int i = terms.length - 1; i >= 0; i--) {
			sb.append("/");
			sb.append(terms[i]);
		}

		// Unfortunately, the email address field is not standard and different
		// formats have different names. Change EMAILADDRESS to Email for the
		// globus format.
		String result = sb.toString();
		result = result.replace("EMAILADDRESS", "Email");

		return result;
	}

	/**
	 * This method will extract a list of X509 certificates from a file.
	 * Unfortunately, the native Java routines are not tolerant of extraneous
	 * information in the file, so we must string out that information manually.
	 * This causes lots of gymnastics for a relatively simple task.
	 * 
	 * @param file
	 * 
	 * @return array of X509Certificates from the file
	 * 
	 * @throws IOException
	 */
	private static X509Certificate[] extractCertificates(File file)
			throws IOException {

		List<X509Certificate> certs = new LinkedList<X509Certificate>();

		// We have to strip out extraneous information by
		// hand. Create a reader to read the file line by
		// line looking for the certificate markers.
		FileInputStream fis = new FileInputStream(file);
		InputStreamReader reader = new InputStreamReader(fis);
		BufferedReader br = new BufferedReader(reader);

		// Store the real information in memory (in a byte
		// array).
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		OutputStreamWriter osw = new OutputStreamWriter(baos);

		// Process the file line by line saving only
		// information between certificate markers (including
		// the markers themselves).
		String line = br.readLine();
		boolean copy = false;
		while (line != null) {
			if (beginTag.equals(line)) {
				copy = true;
			}

			if (copy) {
				osw.write(line);
				osw.write("\n");
			}

			if (endTag.equals(line)) {
				copy = false;
			}

			line = br.readLine();
		}

		// Convert the buffer to a byte array and create an
		// InputStream to read from it.
		osw.close();
		byte[] certInfo = baos.toByteArray();
		ByteArrayInputStream bais = new ByteArrayInputStream(certInfo);

		// Now actually process the embedded certificates.
		// Lots of gymnastics for doing something simple.
		while (bais.available() > 0) {
			try {

				X509Certificate cert;
				cert = (X509Certificate) cf.generateCertificate(bais);
				certs.add(cert);

			} catch (CertificateException ce) {
				throw new RuntimeException(ce.getMessage());
			}
		}

		return certs.toArray(new X509Certificate[certs.size()]);
	}

	/**
	 * A simple filter to only accept files named like *.0; that is, certificate
	 * files.
	 * 
	 * @author loomis
	 */
	private static class CertFilter implements FilenameFilter {

		public boolean accept(File dir, String name) {
			return name.endsWith(".0");
		}
	}

	/**
	 * This class holds information about the subject and issuer of
	 * certificates. With a complete set of certificates, this can be used to
	 * reconstruct a certificate chain.
	 * 
	 * @author loomis
	 * 
	 */
	private static class ChainHolder {

		private HashMap<String, String> chains = new HashMap<String, String>();

		public void add(X500Principal subject, X500Principal issuer) {

			// Extract the DNs of the subject and issuer in the proper format.
			String subjectDN = convertDN(subject);
			String issuerDN = convertDN(issuer);

			if (subjectDN.equals(issuerDN)) {

				// This is a root certificate. Set the "issuer" to the empty
				// string to signal the end of the chain.
				chains.put(subjectDN, "");

			} else {

				// This is an intermediate certificate. Add the real issuer to
				// the map.
				chains.put(subjectDN, issuerDN);

			}

		}

		/**
		 * Extracts the DN chain for a particular certificate. The DN of the
		 * given certificate will be included in the chain. If there is any
		 * error, null will be returned.
		 * 
		 * @param subject
		 * @param issuer
		 * 
		 * @return DN chain for the certificate
		 */
		public String[] getChain(X500Principal subject, X500Principal issuer) {

			String[] result = null;

			// Create a list for the chain itself.
			List<String> chain = new LinkedList<String>();

			// Extract the DNs of the subject and issuer in the proper format.
			String subjectDN = convertDN(subject);
			chain.add(subjectDN);
			subjectDN = convertDN(issuer);

			// Step through each level in the chain saving the DN.
			while (subjectDN != null && !subjectDN.equals("")) {
				chain.add(subjectDN);
				subjectDN = chains.get(subjectDN);
			}

			// If the last subject was not the empty string, then the chain is
			// incomplete. Signal this by returning null.
			if ("".equals(subjectDN)) {
				result = chain.toArray(new String[chain.size()]);
			} else {
				result = new String[0];
			}

			return result;
		}
	}

}
