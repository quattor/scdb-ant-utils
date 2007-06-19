package org.quattor.ant;

import java.io.File;
import java.util.*;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
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
		String tagsBranch = srcUrl.toString();

		// Verify that the working copy is actually from the trunk.
		if (!tagsBranch.endsWith("trunk")) {
			throw new BuildException(
					"working copy must be from repository trunk: " + tagsBranch);
		}

		// Parse the source URL to determine the URL for the tag.
		String tagPath = null;
		int i = tagsBranch.lastIndexOf("/");
		if (i >= 0) {
			tagsBranch = tagsBranch.substring(0, i) + "/tags/";
			tagPath = tagsBranch + tag;
		} else {
			throw new BuildException("found invalid SVN URL: " + tagsBranch);
		}
		SVNURL tagUrl = null;
		try {
			tagUrl = SVNURL.parseURIEncoded(tagPath);
		} catch (SVNException e) {
			throw new BuildException("Error parsing tag URL "+tagPath+": "+e.getMessage());
		}

		// Create a repository instance for tags
		// Check tag branch root exists
		SVNRepository repositoryTags = null;
		try {
			repositoryTags = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(tagsBranch));
		} catch (SVNException e) {
			throw new BuildException(
					"Error creating SVNRepository instance for location "+tagsBranch+": "+e.getMessage());
		}
		ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager();
		repositoryTags.setAuthenticationManager(authManager);
		try {
			SVNNodeKind tagNodeKind = repositoryTags.checkPath("",-1);
			if ( tagNodeKind != SVNNodeKind.DIR ) {
				throw new BuildException(
						"Error : SVN branch "+tagsBranch+" must exist and be a directory");
			}
		} catch ( SVNException e ) {
			throw new BuildException(
					"Error getting information about SVN branch "+tagsBranch+": "+e.getMessage());
		}
		
		// Check for any local or remote modifications. With the flags below,
		// it will do a recursive search of the workspace. The handler will 
		// not be called for ignored or normal (unmodified, uptodate) files.
		System.out.println("Checking for local and remote modifications...");
		try {
			status.doStatus(workspacePath, true, true, false, false, handler);
		} catch (SVNException e) {
			e.printStackTrace();
			throw new BuildException(
					"Check failed; see traceback.");
		}
		if (handler.isModified()) {
			if ( handler.isModifiedLocally() ) {
				System.err.println("Some files found with local modifications : commit your changes before deploy.");
			}
			if ( handler.isModifiedRemotely() ) {
				System.err.println("Some files are not up-to-date : use 'svn update' to update your working copy.");
			}
			throw new BuildException(
					"workspace has local and/or remote modifications; tag aborted");
		}

		// Check the tag branch exists and create it if necessary.
		// The check needs to be done recursively as doMkDir() doesn't allow
		// to create intermediate branch levels implicitly.
		LinkedList<String> branchesToCreate = new LinkedList<String>();
		boolean tagDirExists = false;
		String tagParent = tag;
		SVNNodeKind tagParentNodeKind = null;
		int parentIndex = tagParent.lastIndexOf("/");
		i = parentIndex;
		while ( !tagDirExists && (i>0)) {
			tagParent = tagParent.substring(0,i);
			if ( debugTask )
				System.out.println("Checking existence of tag parent "+tagParent);
			try {
				tagParentNodeKind = repositoryTags.checkPath(tagParent, -1);
			} catch (SVNException e) {
				throw new BuildException(
						"Error checking existence of SVN branch "+tagParent+": "+e.getMessage());
			}
			if ( tagParentNodeKind == SVNNodeKind.NONE ) {
				branchesToCreate.addFirst(tagsBranch+tagParent);
				i = tagParent.lastIndexOf("/");
			} else if ( tagParentNodeKind != SVNNodeKind.DIR ) {
				throw new BuildException(
						"Error: "+tagParent+" exists in repository but is not a directory");
			} else {
				tagDirExists = true;
			}
		}
		if ( ! branchesToCreate.isEmpty() ) {
			SVNURL[] urlsToCreate = new SVNURL[branchesToCreate.size()];
			int j = 0;
			for (Iterator<String> it=branchesToCreate.iterator(); it.hasNext(); ) {
				String branchPath = it.next();
				if ( debugTask )
					System.out.println("Adding "+branchPath+" to branch list to create");
				try {
					urlsToCreate[j] = SVNURL.parseURIEncoded(branchPath);
				} catch ( SVNException e) {
					throw new BuildException(
							"Error converting "+branchPath+" to URL:"+e.getMessage());
				}
				j++;
			}
			System.out.println("Creating tag branch "+tagParent);
			try {
				commit.doMkDir(urlsToCreate,"SCDB ant tools : create new tag branch");
			} catch (SVNException e) {
				String tagRoot = tagsBranch;
				if ( parentIndex > 0 ) {
					tagRoot += tag.substring(0,parentIndex);

				}
				throw new BuildException("Error creating tag branch "+tagRoot+": " + e.getMessage());
			}
		}

		// Actually make the tag.
		System.out.println("Making tag: " + tag);
		try {
			copy.doCopy(srcUrl, SVNRevision.HEAD, tagUrl, false, "ant tag");
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
		 * A private flag to indicate whether there are any local modifications to the
		 * workspace.
		 */
		private boolean modifiedLocally = false;
		
		/**
		 * A private flag to indicate whether there are any remote modifications to the
		 * workspace.
		 */
		private boolean modifiedRemotely = false;
		
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

			// A file is considered locally modified if it is not in a 'normal' status,
			// an ignored file, or an external reference. STATUS_NONE means the file
			// has no local modifications but only remote ones (this happens only if
			// remote=true in doStatus() call.
			// If the file has no modification, check its properties.
			
			SVNStatusType ls = status.getContentsStatus();
			modifiedLocally = (ls != SVNStatusType.STATUS_NORMAL)
					&& (ls != SVNStatusType.STATUS_IGNORED)
					&& (ls != SVNStatusType.STATUS_EXTERNAL)
					&& (ls != SVNStatusType.STATUS_NONE);

			if ( debugHandler ) {
				System.out.println("File="+status.getFile()+", Local status="+ls.toString());
			}

			String localStatus = null;
			if ( modifiedLocally ) {
				localStatus = ls.toString();
			} else {
				SVNStatusType lps = status.getPropertiesStatus();
				modifiedLocally = (lps != SVNStatusType.STATUS_NORMAL)
									&& (lps != SVNStatusType.STATUS_NONE);
				if ( modifiedLocally ) {
					localStatus = "property";
				}
			}
			
			
			// A file is considered remotly modified if the status is not STATUS_NONE 

			SVNStatusType rs = status.getRemoteContentsStatus();
			modifiedRemotely = (rs != SVNStatusType.STATUS_NONE);
			if ( debugHandler ) {
				System.out.println("File="+status.getFile()+", Remote status="+rs.toString());
			}
			
			String remoteStatus = null;
			if ( modifiedRemotely ) {
				remoteStatus = rs.toString();
			} else {
				SVNStatusType rps = status.getRemotePropertiesStatus();
				modifiedRemotely = (rps != SVNStatusType.STATUS_NORMAL)
									&& (rps != SVNStatusType.STATUS_NONE);
				if ( modifiedRemotely ) {
					remoteStatus = "property";
				}
			}
			
			
			// Print message if the file has been modified or is not uptodate
			if ( modifiedLocally || modifiedRemotely ) {
				System.err.println(
						(modifiedLocally ? localStatus : remoteStatus) +
						" (" +
						(modifiedLocally ? "locally" : "") +
						(modifiedLocally && modifiedRemotely ? "," : "") +
						(modifiedRemotely ? "remotely" : "") +
						") : " +
						status.getFile().getPath()
						);
			}
		}

		/**
		 * Get the overall status flag.
		 */
		public boolean isModified() {
			return modifiedLocally || modifiedRemotely;
		}

		/**
		 * Get the local modification status flag.
		 */
		public boolean isModifiedLocally() {
			return modifiedLocally;
		}

		/**
		 * Get the remote modification status flag.
		 */
		public boolean isModifiedRemotely() {
			return modifiedRemotly;
		}

		/**
		 * Reset the status flag.
		 */
		public void reset() {
			modifiedLocally = false;
			modifiedRemotly = false;
		}

	}

}
	
