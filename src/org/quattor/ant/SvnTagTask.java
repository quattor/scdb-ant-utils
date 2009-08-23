package org.quattor.ant;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

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
	
	/* Branch used to create deployment tags in the repository */
	private String tagsBranch = "/tags";

	/* Branch in the repository allowed to deploy */
	private String trunkBranch = "/trunk";

	/* Control printing of debugging messages in this task */
	private boolean debugTask = false;

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
	 * Set the tag name to use.
	 * 
	 * @param tag String containing the name of the tag
	 */
	public void setTag(String tag) {
		this.tag = tag;
	}

	/*
	 * Set the branch in the repository to use for deployment tags.
	 * 
	 * @param tag String containing the name of the branch (relative to the repository root)
	 */
	public void setTagBranch(String branch) {
		if ( !branch.startsWith("/") ) {
			throw new BuildException("Invalid configuration: tagsBranch must start with a /");
		}
		this.tagsBranch = branch;
	}

	/*
	 * Set the branch that must be the source for any deployment.
	 * 
	 * @param tag String containing the name of the branch (relative to the repository root).
	 *            If an empty string, do not check for the source branch.
	 */
	public void setTrunkBranch(String branch) {
		if ( (branch.length() > 0) &&!branch.startsWith("/") ) {
			throw new BuildException("Invalid configuration: trunkBranch must start with a /");
		}
		this.trunkBranch = branch;
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
	@Override
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
	 * repository URL, checks that the local workspace corresponds to trunk branch,
	 * checks for local modifications, checks for remote modifications, and then
	 * makes the tag.
	 */
	private void makeSvnTag() {

		// Create status, copy, and WC (working copy) clients.
		SVNClientManager manager = SVNClientManager.newInstance();
		SVNStatusClient status = manager.getStatusClient();
		SVNCopyClient copy = manager.getCopyClient();
		SVNWCClient wc = manager.getWCClient();
		SVNCommitClient commit = manager.getCommitClient();

		// Create a handler to collect the information.
		StatusHandler handler = new StatusHandler(debugTask);

		// Retrieve the URL for the repository.
		SVNInfo info = null;
		try {
			info = wc.doInfo(workspacePath, SVNRevision.WORKING);
		} catch (SVNException e) {
			throw new BuildException("can't determine working copy URL");
		}

		SVNURL srcUrl = info.getURL();
		String srcBranch = srcUrl.toString();

		// Verify that the working copy is actually from the trunk branch, except if
		// trunkBranch is an empty string (check disabled). Note that standard deployment
		// script do the same check so this is not really necessary...
		if ( (trunkBranch.length() > 0 ) && !srcBranch.endsWith(trunkBranch)) {
			throw new BuildException(
					"working copy is from "+srcBranch+" instead SCDB trunk ("+trunkBranch+")");
		}

		// Parse the source URL to determine the URL for the tag.
		String tagPath = null;
		int i = srcBranch.lastIndexOf("/");
		if (i >= 0) {
			srcBranch = srcBranch.substring(0, i) + tagsBranch + "/";
			tagPath = srcBranch + tag;
		} else {
			throw new BuildException("found invalid SVN URL: " + srcBranch);
		}
		SVNURL tagUrl = null;
		try {
			tagUrl = SVNURL.parseURIEncoded(tagPath);
		} catch (SVNException e) {
			throw new BuildException("Error parsing tag URL " + tagPath + ": "
					+ e.getMessage());
		}

		// Create a repository instance for tags
		// Check tag branch root exists
		SVNRepository repositoryTags = null;
		try {
			repositoryTags = SVNRepositoryFactory.create(SVNURL
					.parseURIEncoded(srcBranch));
		} catch (SVNException e) {
			throw new BuildException(
					"Error creating SVNRepository instance for location "
							+ srcBranch + ": " + e.getMessage());
		}
		ISVNAuthenticationManager authManager = SVNWCUtil
				.createDefaultAuthenticationManager();
		repositoryTags.setAuthenticationManager(authManager);
		try {
			SVNNodeKind tagNodeKind = repositoryTags.checkPath("", -1);
			if (tagNodeKind != SVNNodeKind.DIR) {
				throw new BuildException("Error : SVN branch " + srcBranch
						+ " must exist and be a directory");
			}
		} catch (SVNException e) {
			throw new BuildException(
					"Error getting information about SVN branch " + srcBranch
							+ ": " + e.getMessage());
		}

		// Check for any local or remote modifications. With the flags below,
		// it will do a recursive search of the workspace. The handler will
		// not be called for ignored or normal (unmodified, uptodate) files.
		System.out.println("Checking for local and remote modifications...");
		handler.reset();
		try {
			status.doStatus(workspacePath, true, true, false, false, handler);
		} catch (SVNException e) {
			throw new BuildException("Check failed (" + e.toString() + ")");
		}
		if (handler.isModified()) {
			if (handler.isModifiedLocally()) {
				System.err
						.println("Some files found with local modifications : commit your changes before deploy.");
			}
			if (handler.isModifiedRemotely()) {
				System.err
						.println("Some files are not up-to-date : use 'svn update' to update your working copy.");
			}
			throw new BuildException(
					"workspace has local and/or remote modifications; deploy aborted");
		}

		// Actually make the tag.
		// If the tag branch or the tag parents don't exist, they will be created.
		SVNCommitInfo commitInfo = null;
		System.out.println("Making tag: " + tag);
		try {
			SVNCopySource copySrc = new SVNCopySource(SVNRevision.HEAD,SVNRevision.HEAD,srcUrl);
			commitInfo = copy.doCopy(copySrc, tagUrl, false, true, false, "ant tag");
		} catch (ClassNotFoundException e) {
			throw new BuildException("doCopy() method not found. Check you are using svnkit 1.2+");
		} catch (SVNException e) {
			throw new BuildException("\ntag failed: " + e.getMessage());
		}
		if ( commitInfo.getErrorMessage() != null ) {
			throw new BuildException("\ntag failed: " + commitInfo.getErrorMessage());
		}

	}

	/**
	 * A private class to collect the status information for the subversion
	 * workspace.
	 */
	private static class StatusHandler implements ISVNStatusHandler {

		/**
		 * A private flag to indicate whether there are any local modifications
		 * to the workspace.
		 */
		private boolean localModifications = false;

		/**
		 * A private flag to indicate whether there are any remote modifications
		 * to the workspace.
		 */
		private boolean remoteModifications = false;

		/**
		 * Flag enabling debugging message in the handler
		 */
		private boolean debugHandler = false;

		public StatusHandler(boolean debugTask) {
			debugHandler = debugTask;
		}

		/**
		 * Implement the method to retrieve the status of a file or directory.
		 */
		public void handleStatus(SVNStatus status) {

			// A file is considered locally modified if it is not in a 'normal'
			// status,
			// an ignored file, or an external reference. STATUS_NONE means the
			// file
			// has no local modifications but only remote ones (this happens
			// only if
			// remote=true in doStatus() call.
			// If the file has no modification, check its properties.

			SVNStatusType ls = status.getContentsStatus();
			boolean fileModifiedLocally = (ls != SVNStatusType.STATUS_NORMAL)
					&& (ls != SVNStatusType.STATUS_IGNORED)
					&& (ls != SVNStatusType.STATUS_EXTERNAL)
					&& (ls != SVNStatusType.STATUS_NONE);

			if (debugHandler) {
				System.out.println("File=" + status.getFile()
						+ ", Local status=" + ls.toString());
			}

			String localStatus = null;
			if (fileModifiedLocally) {
				localStatus = ls.toString();
			} else {
				SVNStatusType lps = status.getPropertiesStatus();
				fileModifiedLocally = (lps != SVNStatusType.STATUS_NORMAL)
						&& (lps != SVNStatusType.STATUS_NONE);
				if (fileModifiedLocally) {
					localStatus = "property";
				}
			}

			// A file is considered remotly modified if the status is not
			// STATUS_NONE

			SVNStatusType rs = status.getRemoteContentsStatus();
			boolean fileModifiedRemotely = (rs != SVNStatusType.STATUS_NONE);
			if (debugHandler) {
				System.out.println("File=" + status.getFile()
						+ ", Remote status=" + rs.toString());
			}

			String remoteStatus = null;
			if (fileModifiedRemotely) {
				remoteStatus = rs.toString();
			} else {
				SVNStatusType rps = status.getRemotePropertiesStatus();
				fileModifiedRemotely = (rps != SVNStatusType.STATUS_NORMAL)
						&& (rps != SVNStatusType.STATUS_NONE);
				if (fileModifiedRemotely) {
					remoteStatus = "property";
				}
			}

			// Print message if the file has been modified or is not uptodate
			if (fileModifiedLocally || fileModifiedRemotely) {
				if (fileModifiedLocally) {
					localModifications = true;
				}
				if (fileModifiedRemotely) {
					remoteModifications = true;
				}
				System.err.println((fileModifiedLocally ? localStatus
						: remoteStatus)
						+ " ("
						+ (fileModifiedLocally ? "locally" : "")
						+ (fileModifiedLocally && fileModifiedRemotely ? ","
								: "")
						+ (fileModifiedRemotely ? "remotely" : "")
						+ ") : "
						+ status.getFile().getPath());
			}
		}

		/**
		 * Get the overall status flag.
		 */
		public boolean isModified() {
			return localModifications || remoteModifications;
		}

		/**
		 * Get the local modification status flag.
		 */
		public boolean isModifiedLocally() {
			return localModifications;
		}

		/**
		 * Get the remote modification status flag.
		 */
		public boolean isModifiedRemotely() {
			return remoteModifications;
		}

		/**
		 * Reset the status flag.
		 */
		public void reset() {
			localModifications = false;
			remoteModifications = false;
		}

	}

}
