/*
${license-info}
${developer-info}
${author-info}
*/

package org.quattor.ant;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

public class ProfileInfoTask extends Task implements java.io.FileFilter {

        /*
         *  scdb-ant-utils version
         */
        private final static String version = "${version}";

	/* The output directory for compiled profiles. */
	private String profilesDirName = null;

	/* Name of XML file containing the list of profiles. Default should be appropriate. */
	private String profilesInfoName = "profiles-info.xml";
	
	/* Control printing of debugging messages in this task */
	private boolean debugTask = false;
	
	/* Control printing of informational messages in this task */
	private boolean verbose = true;
	
	/*
	 * Method used by ant to execute this task.
	 */
	@Override
	public void execute() throws BuildException {
		// Sanity checks on the output directory.
		if (profilesDirName == null) {
			throw new BuildException("profilesDirName not specified");
		}
		if (profilesDirName.length() == 0) {
			if ( debugTask ) {
				System.out.println("profilesDirName parameters is an empty string: do nothing.");
			}
			return;
		}
                if ( debugTask ) {
			System.out.println("Checking if profilesDirName ("+profilesDirName+") is a valid directory");
		}
                File outputdir = new File(profilesDirName);
		if (!outputdir.exists()) {
			throw new BuildException("outputdir (" + outputdir
					+ ") does not exist");
		}
		if (!outputdir.isDirectory()) {                   
			throw new BuildException("outputdir (" + outputdir
					+ ") is not a directory");
		}
		
		if ( verbose || debugTask ) {
			System.out.println("Updating "+profilesInfoName+" in "+outputdir);			
		}

		// Get all of the profiles in the given directory.
		StringBuffer contents = new StringBuffer(
				"<?xml version='1.0' encoding='utf-8'?>\n");
		contents.append("<profiles>\n");
		File[] files = outputdir.listFiles(this);
                if (files != null) {
                    for (File file : files) {
                        long mtime = file.lastModified();
                        contents.append("<profile mtime='");
                        contents.append(mtime);
                        contents.append("'>");
                        contents.append(file.getName());
                        contents.append("</profile>\n");
                    }
                } else {
                    throw new BuildException("Files Array is Null");
                }
		contents.append("</profiles>\n");

		// Create the output file.
		File info = new File(outputdir, profilesInfoName);
		try {

			// Open the output file.
			Writer writer = new OutputStreamWriter(new FileOutputStream(info), Charset.forName("utf-8"));
			writer.write(contents.toString());
			writer.close();

		} catch (IOException ioe) {
			throw new BuildException("Can't write profile info file. "
					+ info.getAbsolutePath() + "\n");
		}

	}

	/*
	 * Set the directory for the compiled profiles.
	 *
	 * @param profilesDirName  String output directory path
	 */
	public void setProfilesDirName(String profilesDirName) {
		this.profilesDirName = profilesDirName;
	}

	/**
	 * This implements the FileFilter interface to allow template files to be
	 * selected.  This will accept any non-hidden file except "profile-info.xml".
	 */
	public boolean accept(File file) {
		String name = file.getName();
		boolean ok = (!profilesInfoName.equals(name)) && !file.isHidden();
		return ok;
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
	 * Controls printing of informational messages.
	 * 
	 * @param verbose
	 *            flag to print task debugging information
	 */
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

}
