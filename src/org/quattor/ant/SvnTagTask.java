package org.quattor.ant;

import java.io.File;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNWCClient;

/**
 * This ant task will create a new tag in the repository of the current
 * workspace. The task will check that there are no local or remote changes
 * pending before making the tag and that the given workspace corresponds to the
 * trunk of the repository. If these checks are not satisfied, then the tag will
 * be aborted.
 * 
 * This task assumes the "standard" layout for an subversion repository where
 * the 'trunk' and 'tags' directories are at the same level in the hierarchy.
 * 
 * @author loomis
 * 
 */
public class SvnTagTask extends Task {

	// The repository factory must be setup to know about http/https
	// protocols (DAV) and the svn protocol (SVN).
	static {
		DAVRepositoryFactory.setup();
		SVNRepositoryFactoryImpl.setup();
	}

	/* The name of the tag to use. */
	private String tag = null;

	/* The full path to the local subversion workspace. */
	private File workspacePath = null;

	/*
	 * Set the tag name to use.
	 * 
	 * @param tag String containing the name of the tag
	 */
	public void setTag(String tag) {
		this.tag = tag;
	}

	/*
	 * A File containing the full path to the top-level directory of the current
	 * workspace.
	 * 
	 * @param path String full path to the local workspace.
	 */
	public void setWorkspacePath(File workspacePath) {
		this.workspacePath = workspacePath;
	}

	/*
	 * Method used by ant to execute this task.
	 */
	public void execute() throws BuildException {

		// Verify that all of the required parameters are there.
		if (tag == null) {
			throw new BuildException("tag not specified");
		}
		if (workspacePath == null) {
			throw new BuildException("workspacepath not specified");
		}

		// Parameters are OK. Try to make tag.
		makeSvnTag();
	}

	/**
	 * Make the SVN copy for the tag. This creates the destination URL from the
	 * repository URL, checks that the local workspace corresponds to trunk,
	 * checks for local modifications, checks for remote modifications, and then
	 * makes the tag.
	 */
	private void makeSvnTag() {

		// Create status, copy, and WC (working copy) clients.
		SVNClientManager manager = SVNClientManager.newInstance();
		SVNStatusClient status = manager.getStatusClient();
		SVNCopyClient copy = manager.getCopyClient();
		SVNWCClient wc = manager.getWCClient();

		// Create a handler to collect the information.
		StatusHandler handler = new StatusHandler();

		// Retrieve the URL for the repository.
		SVNInfo info = null;
		try {
			info = wc.doInfo(workspacePath, SVNRevision.WORKING);
		} catch (SVNException e) {
			throw new BuildException("can't determine working copy URL");
		}

		SVNURL srcUrl = info.getURL();
		String url = srcUrl.toString();

		// Verify that the working copy is actually from the trunk.
		if (!url.endsWith("trunk")) {
			throw new BuildException(
					"working copy must be from repository trunk: " + url);
		}

		// Parse the source URL to determine the URL for the tag.
		int i = url.lastIndexOf("/");
		if (i >= 0) {
			url = url.substring(0, i) + "/tags/" + tag;
		} else {
			throw new BuildException("found invalid SVN URL: " + url);
		}

		// Create the destination URL from the new string value.
		SVNURL destUrl = null;
		try {
			destUrl = SVNURL.parseURIDecoded(url);
		} catch (SVNException e) {
			throw new BuildException("error creating dest. URL: " + url);
		}

		// Check for any local modifications. With the flags below,
		// it will do a recursive search of the workspace path but
		// only use local information. The handler will not be called
		// for ignored or normal files.
		System.out.println("Checking for local modifications...");
		try {
			status.doStatus(workspacePath, true, false, false, false, handler);
		} catch (SVNException e) {
			e.printStackTrace();
			throw new BuildException(
					"svn status (local) failed; see traceback.");
		}
		if (handler.isModified()) {
			throw new BuildException(
					"workspace has local modifications; tag aborted");
		}

		// Check for any remote modifications. With the flags below,
		// it will do a recursive search of the workspace path and
		// will use remote information. The handler will not be
		// called for ignored or normal files.
		handler.reset();
		System.out.println("Checking for remote modifications...");
		try {
			status.doStatus(workspacePath, true, true, false, false, handler);
		} catch (SVNException e) {
			e.printStackTrace();
			throw new BuildException(
					"svn status (remote) failed; see traceback.");
		}
		if (handler.isModified()) {
			throw new BuildException(
					"workspace needs to be updated; tag aborted");
		}

		// Actually make the tag.
		System.out.println("Making tag: " + tag);
		try {
			copy.doCopy(srcUrl, SVNRevision.HEAD, destUrl, false, "ant tag");
		} catch (SVNException e) {
			throw new BuildException("tag failed: " + e.getMessage());
		}

	}

	/**
	 * A private class to collect the status information for the subversion
	 * workspace.
	 */
	private class StatusHandler implements ISVNStatusHandler {

		/**
		 * A private flag to indicate whether there are any modifications to the
		 * workspace.
		 */
		private boolean modified = false;

		/**
		 * Implement the method to retrieve the status of a file or directory.
		 */
		public void handleStatus(SVNStatus status) {

			SVNStatusType s = status.getContentsStatus();

			// A file is considered modified if it is not in a 'normal' status,
			// an ignored file, or an external reference.
			boolean fileModified = (s != SVNStatusType.STATUS_NORMAL)
					&& (s != SVNStatusType.STATUS_IGNORED)
					&& (s != SVNStatusType.STATUS_EXTERNAL);

			// Write the files that have been modified to the standard error.
			if (fileModified) {
				modified = true;
				System.err.println("modified : " + status.getFile());
			}
		}

		/**
		 * Get the status flag.
		 */
		public boolean isModified() {
			return modified;
		}

		/**
		 * Reset the status flag.
		 */
		public void reset() {
			modified = false;
		}

	}

}
