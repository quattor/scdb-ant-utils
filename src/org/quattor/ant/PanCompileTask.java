package org.quattor.ant;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.DirSet;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;

/**
 * This task compiles pan templates with the c-implementation of the pan
 * language compiler.
 * 
 * @author loomis
 * @deprecated
 * 
 */
@Deprecated
public class PanCompileTask extends Task implements java.io.FileFilter {

	/* Determine whether or not to print debug information. */
	private boolean debug = false;

	/* The command for the pan compiler. */
	private String command = "/opt/edg/bin/panc";

	/* The output directory for compiled profiles. */
	private File outputdir = null;

	/* The style of the XML output. */
	private String xmlStyle = "pan";

	/* Whether or not to output dependency information. */
	private boolean dependency = true;

	/* The list of files to check. */
	private LinkedList<File> files = new LinkedList<File>();

	/* The list of directories to include in search path. */
	private LinkedList<File> path = new LinkedList<File>();

	/* A comma- or space-separated list of file globs. */
	private List<DirSet> includes = new LinkedList<DirSet>();

	/* The root directory for the includes. */
	private File includeroot = null;

	/* The pattern in the dependency files. (Two double-quoted strings). */
	static Pattern depline = Pattern.compile("\"(.*)\"\\s+\"(.*)\"");

	/*
	 * Method used by ant to execute this task.
	 */
	@Override
	public void execute() throws BuildException {

		// Sanity checks on the output directory.
		if (outputdir == null) {
			throw new BuildException("outputdir not specified");
		}
		if (!outputdir.exists()) {
			throw new BuildException("outputdir (" + outputdir
					+ ") does not exist");
		}
		if (!outputdir.isDirectory()) {
			throw new BuildException("outputdir (" + outputdir
					+ ") is not a directory");
		}

		// If some include globs were specified, then check that the
		// includeroot was specified. Add the necessary paths.
		if (includes.size() > 0) {
			if (includeroot == null) {
				throw new BuildException(
						"includeroot must be specified to use 'includes' parameter");
			}

			Path antpath = new Path(getProject());

			for (DirSet dirset : includes) {
				dirset.setDir(includeroot);
				antpath.addDirset(dirset);
			}
			addPaths(antpath);
		}

		// Summary of how many templates were specified.
		System.out.println(files.size() + " object template(s) specified");

		// Check to see if compilation is really necessary.
		removeCurrentProfiles();
		if (files.size() == 0) {
			System.out.println("All targets are up-to-date.");
			return;
		}

		// Print out a summary of the results.
		System.out
				.println("Compiling " + files.size() + " object template(s).");

		// Build the static part of the command to execute.
		LinkedList<String> cmd = new LinkedList<String>();
		cmd.add(command);
		cmd.add("--xml-style=" + xmlStyle);
		if (dependency) {
			cmd.add("--dependency");
		}
		cmd.add("--output-dir=" + outputdir.getAbsolutePath());
		for (File dir : path) {
			cmd.add("--include-dir=" + dir.getAbsolutePath());
		}

		for (File file : files) {
			cmd.add(file.getAbsolutePath());
		}

		// Print out the exact command used if the debug flag is set.
		if (debug) {
			StringBuffer sb = new StringBuffer();
			for (String s : cmd) {
				sb.append(s);
				sb.append(" ");
			}
			System.out.println(sb);
		}

		// Create the process builder to manage the subprocesses.
		ProcessBuilder pb = new ProcessBuilder(cmd);
		
		// Get the start time.
		Date start = new Date();

		// Loop over each file and check the syntax. The pan compiler
		// allows multiple arguments, but avoid using that to avoid
		// system command length limits.
		try {

			// Start the process, wait for it, then harvest the return
			// code.
			try {
				Process p = pb.start();

				// Transfer bytes to error stream.
				try {
					InputStream is = p.getErrorStream();
					for (int b = is.read(); b > 0; b = is.read()) {
						System.err.write(b);
					}
					is.close();
				} catch (IOException ioe) {
					ioe.printStackTrace();
				}

				p.waitFor();
				if (p.exitValue() != 0) {
					throw new BuildException(
							"Compilation failed; see error log.");
				}

			} catch (InterruptedException ie) {
				System.err.println("Compilation interrupted...");
				ie.printStackTrace();
			}

		} catch (IOException ioe) {
			// This indicates a serious error when running command.
			// Like the executable doesn't exist, permissions aren't
			// correct, etc. Abort the full run.
			ioe.printStackTrace();
			throw new BuildException("Error running command: " + cmd + ".\n"
					+ ioe.getMessage() + "\n");
		}
		
		Date end = new Date();
		System.out.println("Total elapsed time (ms): "+(end.getTime()-start.getTime()));

	}

	/*
	 * Set the command to use for the pan compiler. This defaults to
	 * /opt/edg/bin/panc.
	 * 
	 * @param command String containing full path of the command
	 */
	public void setCommand(String command) {
		this.command = command;
	}

	/*
	 * Set the directory for the compiled profiles.
	 * 
	 * @param outputdir File containing output directory
	 */
	public void setOutputdir(File outputdir) {
		this.outputdir = outputdir;
	}

	/*
	 * Set the XML style for the output. Allowed values are pan, compact, lcfg,
	 * piotr, tim, and xmldb. The style "pan" is the default.
	 * 
	 * @param xmlStyle style for the XML output
	 */
	public void setXMLStyle(String xmlStyle) {
		if (!("pan".equals(xmlStyle) || "xmldb".equals(xmlStyle) || "compact"
				.equals(xmlStyle))) {
			throw new BuildException("invalid XML style: " + xmlStyle);
		}
		this.xmlStyle = xmlStyle;
	}

	/*
	 * Set whether to print debug information. If set, this will print out the
	 * complete command used to build the machine configuration files.
	 * 
	 * @param debug flag to print debug information
	 */
	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	/*
	 * Set whether a file containing the dependency information should created
	 * or not.
	 * 
	 * @param dependency flag to output dependency information or not
	 */
	public void setDependency(boolean dependency) {
		this.dependency = dependency;
	}

	/*
	 * Set the directory to use for the include globs. This is required only if
	 * the includes parameter is set.
	 * 
	 * @param includeroot File giving the root directory for the include globs
	 */
	public void setIncludeRoot(File includeroot) {

		// Do some parameter checking. The parameter must be an
		// existing directory.
		if (!includeroot.exists()) {
			throw new BuildException("includeroot doesn't exist: "
					+ includeroot);
		}
		if (!includeroot.isDirectory()) {
			throw new BuildException("includeroot must be a directory: "
					+ includeroot);
		}
		this.includeroot = includeroot;
	}

	/*
	 * Set the include globs to use for the panc loadpath.
	 * 
	 * @param includes String of comma- or space-separated file globs
	 */
	public void setIncludes(String includes) {

		// Split the string into separate file globs.
		String[] globs = includes.split("[\\s,]+");

		// Loop over these globs and create dirsets from them.
		// Do not set the root directory until the task is
		// executed.
		for (String glob : globs) {
			DirSet dirset = new DirSet();
			dirset.setIncludes(glob);
			this.includes.add(dirset);
		}
	}

	/*
	 * Support nested fileset elements. This is called by ant only after all of
	 * the children of the fileset have been processed. These are the object
	 * templates to process.
	 * 
	 * @param fileset a configured FileSet
	 */
	public void addConfiguredFileSet(FileSet fileset) {
		if (fileset != null)
			addFiles(fileset);
	}

	/*
	 * Collect all of the files listed within enclosed fileset tags.
	 * 
	 * @param fs FileSet from which to get the file names
	 */
	private void addFiles(FileSet fs) {

		// Get the files included in the fileset.
		DirectoryScanner ds = fs.getDirectoryScanner(getProject());

		// The base directory for all files.
		File basedir = ds.getBasedir();

		// Loop over each file creating a File object.
		for (String f : ds.getIncludedFiles()) {
			files.add(new File(basedir, f));
		}
	}

	/*
	 * Support nested path elements. This is called by ant only after all of the
	 * children of the path have been processed. These are the include
	 * directories to find non-object templates. Non-directory elements will be
	 * silently ignored.
	 * 
	 * @param path a configured Path
	 */
	public void addConfiguredPath(Path path) {
		if (path != null)
			addPaths(path);
	}

	/*
	 * Collect all of the directories listed within enclosed path tags. Order of
	 * the path elements is preserved. Duplicates are included where first
	 * mentioned.
	 * 
	 * @param p Path containing directories to include in compilation
	 */
	private void addPaths(Path p) {

		for (String d : p.list()) {
			File dir = new File(d);
			if (dir.exists() && dir.isDirectory()) {
				if (!path.contains(dir))
					path.add(dir);
			}
		}
	}

	/*
	 * Check to see if the profiles are current. Checks to see if candidate
	 * output files exist for all object templates and then checks the
	 * modification times.
	 */
	private void removeCurrentProfiles() {

		// Loop over all of the files and create a list of those which
		// are current.
		LinkedList<File> current = new LinkedList<File>();
		for (File f : files) {

			// Map the file into the output file and the dependency
			// file.
			String name = f.getName();
			File t, d;
			if (name.endsWith(".tpl")) {
				t = new File(outputdir, name.substring(0, name.length() - 4)
						+ ".xml");
				d = new File(outputdir, name.substring(0, name.length() - 4)
						+ ".xml.dep");
			} else {
				t = new File(outputdir, name + ".xml");
				d = new File(outputdir, name + ".xml.dep");
			}

			// Only do detailed checking if both the output file and
			// the dependency file exist.
			if (!(t.exists() && d.exists()))
				continue;

			// The modification time of the target xml file.
			long targetTime = t.lastModified();

			// The dependency file must have been generated at the
			// same time or after the xml file.
			if (d.lastModified() < t.lastModified()) {
				System.out.println("Dependency file not current: " + d);
				continue;
			} else if (!d.canRead()) {
				System.out.println("Can't read dependency file: " + d);
				continue;
			}

			// Compare the target file with the youngest of the dependencies
			// (i.e. the largest modification time). Also check that none
			// of the files has changed position in the load path.
			long depTime = Long.MIN_VALUE;
			try {

				Scanner scanner = new Scanner(d);
				try {
					while (depTime < targetTime) {
						String line = scanner.nextLine();

						Matcher matcher = depline.matcher(line);
						// Check that the line matches the correct pattern.
						if (matcher.matches()) {

							String templateName = matcher.group(1).replace('/',
									File.separatorChar)
									+ ".tpl";
							String templatePath = matcher.group(2);

							File dep = new File(templatePath + templateName)
									.getAbsoluteFile();
							if (dep.exists()) {
								long mtime = dep.lastModified();
								if (mtime > depTime)
									depTime = mtime;
							} else {
								depTime = Long.MAX_VALUE;
								break;
							}

							// Check that the location hasn't changed in the
							// path. If it has changed, then profile isn't
							// current.
							for (File pathdir : path) {
								File check = new File(pathdir, templateName);
								if (check.exists()) {
									if (!dep.equals(check))
										depTime = Long.MAX_VALUE;
									break;
								}
							}

							// If the file hasn't been found at all, then do
							// nothing. The file may not have been found on the
							// load path for a couple of reasons: 1) it is the
							// object file itself which may not be on the load
							// path and 2) the internal loadpath variable may be
							// used to find the file. In the second case, rely
							// on the explicit list of dependencies to pick up
							// changes. NOTE: this check isn't 100% correct. It
							// is possible to move templates around in the
							// "internal" load path; these changes will not be
							// picked up correctly.
						}
					}
				} catch (java.util.NoSuchElementException nsep) {
					// Do nothing. There are no more lines in file.
				}

			} catch (java.io.FileNotFoundException fnfe) {

				// If dependency file isn't found, then this will force a
				// rebuild.
				depTime = Long.MAX_VALUE;
			}

			// The output file is current if it is younger
			// than the youngest dependency.
			if (depTime < targetTime)
				current.add(f);
		}

		// Delete current files from the list of profiles to process.
		files.removeAll(current);

		// Print out how many profiles are current.
		System.out.println(current.size()
				+ " object template(s) are up-to-date");
	}

	/**
	 * This implements the FileFilter interface to allow template files to be
	 * selected.
	 */
	public boolean accept(File file) {
		return (file.getName().endsWith(".tpl"));
	}

}
