package org.quattor.ant;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;

/**
 * This task checks pan template syntax with the c-implementation of the pan
 * language compiler.
 * 
 * @author loomis
 * @deprecated
 * 
 */
public class PanSyntaxTask extends Task {

	/* The command for the pan compiler. */
	private String command = "/usr/bin/panc";

	/* The file to use for the timestamp. */
	private File timestamp = null;

	/* The list of files to check. */
	private LinkedList<String> files = new LinkedList<String>();
	
	/* Print debugging messages */
	private boolean debugTask = false;

	/*
	 * Method used by ant to execute this task.
	 */
	public void execute() throws BuildException {

		// Print out a summary of the results.
		System.out.println("Checking " + files.size() + " template(s).");

		// Build the static part of the command to execute.
		ArrayList<String> cmd = new ArrayList<String>(3);
		cmd.add(0, command);
		cmd.add(1, "--check");
		cmd.add(2, "");

		// Create the process builder to manage the subprocesses.
		ProcessBuilder pb = new ProcessBuilder(cmd);

		// Loop over each file and check the syntax. The pan compiler
		// allows multiple arguments, but avoid using that to avoid
		// system command length limits.
		try {
			int failed = 0;
			for (String f : files) {

				// Changes in the cmd list are picked up by the
				// process builder. Use this to start each child with
				// different filename. NOTE: The quotes are needed to
				// deal with path names with embedded spaces (which
				// occurs frequently on windows).
				cmd.set(2, f);

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
						failed++;
						System.err.println("Syntax error: " + f);
					}

				} catch (InterruptedException ie) {
					System.err.println("Interrupted command: " + cmd.get(0)
							+ " " + cmd.get(1) + " " + cmd.get(2));
					ie.printStackTrace();
				}

			}

			// Ensure that ant knows of the problems with the build.
			if (failed > 0) {
				throw new BuildException(failed
						+ " template(s) failed syntax check");
			}

		} catch (IOException ioe) {
			// This indicates a serious error when running command.
			// Like the executable doesn't exist, permissions aren't
			// correct, etc. Abort the full run.
			ioe.printStackTrace();
			throw new BuildException("Error running command: " + cmd.get(0)
					+ " " + cmd.get(1) + " " + cmd.get(2) + ".\n"
					+ ioe.getMessage() + "\n");
		}

		// If there were no problems, then touch the timestamp file.
		try {
			if (timestamp != null && !timestamp.createNewFile()) {
				timestamp.setLastModified(System.currentTimeMillis());
			}
		} catch (IOException ioe) {
			System.err.println("Error touching timestamp file.");
			System.err.println(ioe.getMessage());
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
	 * Set the file to use for a timestamp.
	 * 
	 * @param command String containing full path of the command
	 */
	public void setTimestamp(File timestamp) {
		this.timestamp = timestamp;
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
	 * Collect all of the files listed within enclosed fileSet tags.
	 * 
	 * @param fs FileSet from which to get the file names
	 */
	private void addFiles(FileSet fs) {

		// If the timestamp file exists, then get the modification
		// time. Default to minimum long value.
		long mtime = Long.MIN_VALUE;
		if (timestamp != null && timestamp.exists()) {
			mtime = timestamp.lastModified();
		}

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
			if (file.exists() && (file.lastModified() >= mtime)) {
				files.add(file.getAbsolutePath());
			}
		}
	}

}
