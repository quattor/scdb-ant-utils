/*
${license-info}
${developer-info}
${author-info}
*/

package org.quattor.ant;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;

public class RepositoryTask extends Task {

        /*
         *  scdb-ant-utils version
         */
        private final String version = "${version}";

	/* The pattern for matching the name information. */
	private static Pattern namePattern = Pattern
			.compile("\\s*#\\s*name\\s*=\\s*([^\\s]+)\\s*");

	/* The pattern for matching the owner information. */
	private static Pattern ownerPattern = Pattern
			.compile("\\s*#\\s*owner\\s*=\\s*([^\\s]+)\\s*");

	/* The pattern for matching the url information. */
	private static Pattern urlPattern = Pattern
			.compile("\\s*#\\s*url\\s*=\\s*([^\\s]+)\\s*");

	/* The pattern for matching the package information. */
	private static Pattern pkgPattern = Pattern
			.compile("\\s*#\\s*pkg\\s*=\\s*([^\\s]+)\\s*");

	/* The pattern for matching the template name information. */
	private static Pattern templatePattern = Pattern
			.compile("\\s*structure\\s*template\\s*([^\\s]+)\\s*;");

	/* The pattern for matching the name PAN property. */
	private static Pattern namePanPattern = Pattern
			.compile("\\s*[\"']name[\"']\\s*=\\s*[\"']([^\\s]+)[\"']\\s*;\\s*");

	/*
	 * The pattern for matching an anchor with href attribute (and ending with
	 * rpm).
	 */
	private static Pattern hrefPattern = Pattern.compile(
			"<a\\s+.*?href=\"(.*?\\.rpm)\"\\s*>", Pattern.CASE_INSENSITIVE
					| Pattern.MULTILINE);

	/* The list of configuration files. */
	private LinkedList<File> files = new LinkedList<File>();

	/* Control printing of debugging messages in this task */
	private boolean debugTask = false;
	
	/* Control generation of template listing repositories */
	private boolean genList = false;

	/* the name of the directory containing generated template */
	private String nameListDir = null;

	/* Namespace of the template defining the list of all repositories configured */
	private String listName = "repository/allrepositories";

	
	/*
	 * Method used by ant to execute this task.
	 */
	@Override
	public void execute() throws BuildException {
		// need to get path as parameter
		String listFileName = listName+".pan";
		LinkedList<Repository> repositoryList = new LinkedList<Repository>();
		
		// Loop over all of the given files. Write those that are repository
		// templates with the proper embedded comments and that have changed.
		for (File f : files) {
			Repository r = parseTemplate(f);
			if (r != null) {
				if (r.write()) {
					System.out.println("Updating: " + r);
				}
				repositoryList.add(r);
			}
		}

		// Create a template defining a variable with all existing repositories
		// if genList is true
		if (this.genList) {			
			if (this.nameListDir == null) {
				throw new BuildException("Cannot build template with list of repositories: option 'nameListdir' undefined.");
			} else {
				listFileName = this.nameListDir+"/"+listFileName;			
			}
			if (this.debugTask) {
				System.out.println("Generating template with list of repositories");
				System.out.println("Template location "+listFileName);
			}
		
			try {
                                System.out.println("Updating "+listFileName);
				FileWriter allRepos = new FileWriter(listFileName,false);
				allRepos.write("# List of all existing repository templates\n");
				allRepos.write("template "+listName+";\n\n");
				allRepos.write("variable ALL_REPOSITORIES= nlist( \n");
				for (Repository r:repositoryList) {
					allRepos.write("'"+r.name+"',  create('repository/" + r.name+"'),\n");					
				}
				allRepos.write("); \n");
				allRepos.close();
				}
			catch (Exception e) {
				throw new BuildException("Error creating list of all configured repositories: " + e.getMessage());
			}		
		}
			
		
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

	/**
	 * Setting this flag will generate a template with a list of all repositories.
	 *  
	 * @param genList
	 *            flag to create template listing all repositories
	 */
	public void setGenList(boolean genList) {
		this.genList = genList;
	}
	
	/**
	 * Set the directory for the generated repository list template.
	 * 
	 * @param nameParamDirTpl
	 *            String containing full path of the directory
	 * 
	 */
	public void setNameListDir(String nameListDir) {
		this.nameListDir = nameListDir;
	}
	
	/**
	 * Set the namespace for the generated repository list template.
	 * 
	 * @param listNamespace
	 *            String containing namespace
	 * 
	 */
	public void setListName(String listNamespace) {
		this.listName = listNamespace;
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
	 * Collect all of the files listed within enclosed fileSet tags. These
	 * contain the name, owner, and URL for each package repository.
	 * 
	 * @param fs FileSet from which to get the file names
	 */
	private void addFiles(FileSet fs) {

		// Get the files included in the fileset.
		DirectoryScanner ds = fs.getDirectoryScanner(getProject());

		// The base directory for all files.
		File basedir = ds.getBasedir();

		// Loop over each file creating a File object.
		// for (String f : ds.getIncludedFiles()) {
		String[] temp = ds.getIncludedFiles();
		for (int i = 0; i < temp.length; i++) {
			String f = temp[i];

			File file = new File(basedir, f);
			if (file.exists()) {
				files.add(file);
			}
		}
	}

	/**
	 * Parse a repository template for the name, owner, URL, and pkg tags. This
	 * code does NOT attempt to parse the pan syntax; instead this information
	 * should be embedded in comments in the template header. The format is "#
	 * name = <name>" for the repository name; use a similar syntax for the
	 * owner and URL. If 'structure template' line exists in the template, the
	 * template name is retrieved from this line to be preserved if updating the
	 * template.
	 */
	public Repository parseTemplate(File f) {

		Repository repository = null;

		// Setup the values for the repository.
		File template = f;
		String name = null;
		String nameProperty = null;
		String owner = null;
		String templateName = null;
		URL url = null;
		TreeSet<String> packages = new TreeSet<String>();

		LineNumberReader reader = null;
		try {

			// Open the file for reading.
			reader = new LineNumberReader(new FileReader(template));

			// Loop over all lines searching for key/value pair matches in
			// comment lines.
			// If more than one line has the same value, the later one is used.
			// The whole file is parsed to build the list of current packages
			// used
			// later to check if repository template content has changed.
			String line = reader.readLine();
			while (line != null) {

				Matcher m = namePattern.matcher(line);
				if (m.matches()) {
					name = m.group(1);
				}

				m = ownerPattern.matcher(line);
				if (m.matches()) {
					owner = m.group(1);
				}

				m = urlPattern.matcher(line);
				if (m.matches()) {
					try {
						url = new URL(m.group(1));
					} catch (MalformedURLException mul) {
						// Consumed exception.
					}
				}

				m = pkgPattern.matcher(line);
				if (m.matches()) {
					packages.add(m.group(1));
				}

				m = templatePattern.matcher(line);
				if (m.matches()) {
					templateName = m.group(1);
				}

				m = namePanPattern.matcher(line);
				if (m.matches()) {
					nameProperty = m.group(1);
				}

				line = reader.readLine();
			}

		} catch (java.io.IOException ioe) {
			throw new BuildException("Error reading template "
					+ template.getAbsolutePath() + ": (" + ioe.toString() + ")");
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException ioe) {
					throw new BuildException("Error closing template "
							+ template.getAbsolutePath() + ": ("
							+ ioe.toString() + ")");
				}
			}
		}

		if ((name != null) && (owner != null) && (url != null)) {
			repository = new Repository(template, name, owner, url, packages,
					templateName, nameProperty, debugTask);
		} else if (debugTask) {
			System.out.println("Template " + template.getAbsolutePath()
					+ " not recognized as a template");
			System.out.println("    template=" + template);
			System.out.println("    name="
					+ (name != null ? name : "not specified"));
			System.out.println("    url="
					+ (url != null ? url : "not specified"));
			System.out.println("    owner="
					+ (owner != null ? owner : "not specified"));
		}

		return repository;
	}

	/**
	 * This takes a package name extracted as an HREF attribute checks that it
	 * has the form of an RPM package and returns a key/value pair. The key is
	 * the formatted name of the packages; the value is a line appropriate for a
	 * pan repository template. On any error, this returns null.
	 */
	private static String[] formatPkg(String pkg) {

		String[] pair = null;

		if (pkg != null) {

			// Split this to get the package name and version.
			Pattern p = Pattern
					.compile("\\s*(.+)-((?:[^-]+)-(?:[^-]+))\\.([^\\.]+)\\.rpm\\s*$");
			Matcher m = p.matcher(pkg);
			if (m.matches()) {
				String name = m.group(1);
				String version = m.group(2);
				String arch = m.group(3);

				pair = new String[2];
				pair[0] = name + "-" + version + "-" + arch;
				pair[1] = "# pkg = " + pair[0] + "\n" + "escape(\"" + pair[0]
						+ "\"),nlist(\"name\",\"" + name + "\",\"version\",\""
						+ version + "\",\"arch\",\"" + arch + "\")";
			}
		}

		return pair;
	}

	/**
	 * A private class which just encapsulates the name, owner, and URL of a
	 * repository. Using those values it can then generate a pan template
	 * describing that repository.
	 */
	private static class Repository {

		private final File template;

		private final String name;

		private final String nameProperty;

		private final String owner;

		private String templateName;

		private final URL url;

		private final Set<String> existingPkgs;

		private final boolean debugTask;

		/**
		 * Create a new repository based on the given values.
		 */
		public Repository(File template, String name, String owner, URL url,
				Set<String> packages, String templateName, String nameProperty,
				boolean debugTask) {
			this.template = template;
			this.name = name;
			this.nameProperty = nameProperty;
			this.owner = owner;
			this.templateName = templateName;
			this.url = url;
			this.existingPkgs = new TreeSet<String>();
			this.existingPkgs.addAll(packages);
			this.debugTask = debugTask;
		}

		/**
		 * Write the template to the given file.
		 * 
		 * Returns true if the template was rebuilt, false otherwise.
		 */
		public boolean write() {

			// Parse the URL for the template and extract the listed packages.
			HashMap<String, String> pkgs = new HashMap<String, String>();
			parseURL(pkgs);

			// Compare the packages with what already exists. If the packages
			// are the same, then there is nothing to do with the following
			// exceptions :
			// - If no 'structure template' line has been found, existing
			// template is
			// malformed and need to be rebuilt, even if the package list is the
			// same.
			// - If 'name' property is missing or its value differs from 'name'
			// tag in comments,
			// In this case, the template is considered malformed and must be rebuilt.
			
			Set<String> newPkgs = pkgs.keySet();
			boolean write = !(newPkgs.equals(existingPkgs))
					|| (templateName == null) || !(name.equals(nameProperty));

			// Write out the template if necessary.
			if (write) {

				if (debugTask) {
					if (templateName == null)
						System.out.println(template
								+ ": no 'structure template line' found");
					if (!name.equals(nameProperty))
						System.out.println(template + ": 'name' tag (" + name
								+ ") doesn't match 'name' property ("
								+ nameProperty + ")");
				}

				String contents = format(pkgs);

				if (contents != null) {

					try {
						FileWriter writer = new FileWriter(template);
						writer.write(contents);
						writer.close();
					} catch (IOException ioe) {
						throw new BuildException("Error writing template "
								+ template.getAbsolutePath() + ": ("
								+ ioe.toString() + ")");
					}
				}
			}
			return write;
		}

		/**
		 * Generate a template for this repository.
		 */
		public String format(Map<String, String> pkgs) {

			if (templateName == null) {
				templateName = "repository/" + name;
			}

			StringBuilder buffer = new StringBuilder();
			buffer.append("#\n");
			buffer.append("# Generated by RepositoryTask on "
					+ DateFormat.getInstance().format(new Date()) + "\n");
			buffer.append("#\n");
			buffer.append("# name = " + name + "\n");
			buffer.append("# owner = " + owner + "\n");
			buffer.append("# url = " + url + "\n");
			buffer.append("#\n\n");
			buffer.append("structure template " + templateName + ";" + "\n\n");
			buffer.append("\"name\" = \"" + name + "\";" + "\n");
			buffer.append("\"owner\" = \"" + owner + "\";" + "\n");
			buffer.append("\"protocols\" = list(" + "\n");
			buffer.append("  nlist(\"name\",\"http\"," + "\n");
			buffer.append("        \"url\",\"" + url + "\")" + "\n");
			buffer.append(");\n\n");

			buffer.append("\"contents\" = nlist(\n");

			TreeSet<String> keys = new TreeSet<String>();
			keys.addAll(pkgs.keySet());

			for (String s : keys) {
				buffer.append(pkgs.get(s));
				buffer.append(",\n");
			}
			buffer.append(");\n");

			return buffer.toString();
		}

		/**
		 * Parse the HTML document returned by the given URL. This extracts all
		 * of the anchors looking for ones which reference RPM packages. This is
		 * done by the file extension.
		 */
		private void parseURL(Map<String, String> pkgs) {

			try {

				// Get an input stream for the URL.
				InputStreamReader is = new InputStreamReader(url.openStream());

				// Create a character buffer for holding the data while
				// transferring it to a string buffer.
				int bufferSize = 1024;
				char[] buffer = new char[bufferSize];
				StringBuilder sb = new StringBuilder(2 * bufferSize);

				for (int nchars = is.read(buffer, 0, bufferSize); nchars >= 0; nchars = is
						.read(buffer, 0, bufferSize)) {
					sb.append(buffer, 0, nchars);
				}

				is.close();

				// Now loop over all anchors in the file, extracting the
				// necessary info.
				Matcher matcher = hrefPattern.matcher(sb);
				while (matcher.find()) {
					String[] pkg = formatPkg(matcher.group(1));
					if (pkg != null)
						pkgs.put(pkg[0], pkg[1]);
				}

			} catch (java.io.IOException ioe) {
				throw new BuildException("Error getting RPM list from URL "
						+ url + ": (" + ioe.toString() + ")");
			}

		}

		@Override
		public String toString() {
			return template.getAbsolutePath();
		}

	}

}
