/*
${license-info}
${developer-info}
${author-info}
*/

package org.quattor.ant;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

/**
 * This task is intended to maintain a working copy as a cache on the Quattor
 * server. This cache is used to generate the "official" copies of the machine
 * profiles.
 * 
 * @author loomis
 * 
 */
public class SvnCacheTask extends Task {

        /*
         *  scdb-ant-utils version
         */
        private final static String version = "${version}";

	// The repository factory must be setup to know about http/https
	// protocols (DAV) and the svn protocol (SVN).
	static {
		DAVRepositoryFactory.setup();
		SVNRepositoryFactoryImpl.setup();
	}

	/*
	 * The name of the tag. The string "tags/" will be prepended to the tag,
	 * unless the tag is "trunk".
	 */
	private String tag = null;

	/* The full path to the local subversion working copy. */
	private File wcPath = null;

	/* The URL to the repository. */
	private SVNURL repositoryUrl = null;

	/* The maximum number of times to try recover a failed switch. */
	private int recoveryLimit = 1;

	/* User name for accessing the repository. */
	private String username = null;

	/* The password to access the repository. */
	private String password = null;

	/**
	 * Give the username that will be used to access the subversion repository.
	 * 
	 * @param username
	 *            username for the repository
	 */
	public void setUsername(String username) {
		this.username = username;
	}

	/**
	 * Give the password that will be used to access the subversion repository.
	 * 
	 * @param password
	 *            plain-text password
	 */
	public void setPassword(String password) {
		this.password = password;
	}

	/**
	 * This task will attempt to recover from failed switches or checkouts the
	 * number of times given here. The recovery procedure is to retry updates
	 * until success or until the limit is reached. This can only be attempted
	 * if the working copy URL was successfully changed to the tag.
	 * 
	 * @param limit
	 *            maximum number of recovery attempts
	 */
	public void setRecoveryLimit(int limit) {
		if (limit > 0) {
			recoveryLimit = limit;
		} else {
			recoveryLimit = 0;
		}
	}

	/**
	 * Repository to be used for the build on the server.
	 * 
	 * @param repositoryUrl
	 *            URL for the repository
	 */
	public void setRepositoryUrl(String repositoryUrl) {
		try {
			URL url = new URL(repositoryUrl);
			this.repositoryUrl = SVNURL.parseURIEncoded(url.toExternalForm());
		} catch (SVNException se) {
			throw new BuildException("invalid URL: " + repositoryUrl + "\n"
					+ se.getMessage());
		} catch (MalformedURLException mue) {
			throw new BuildException("malformed URL: " + repositoryUrl);
		}
	}

	/**
	 * Tag name to use without the usual "tags/" prefix.
	 * 
	 * @param tag
	 *            name of the tag
	 */
	public void setTag(String tag) {
		this.tag = tag;
	}

	/**
	 * A File containing the full path to the top-level directory of the current
	 * workspace.
	 * 
	 * @param path
	 *            String full path to the local workspace.
	 */
	public void setWorkspacePath(File workspacePath) {
		this.wcPath = workspacePath;
	}

	/*
	 * Method used by ant to execute this task.
	 */
	@Override
	public void execute() throws BuildException {

		// Verify that all of the required parameters are there.
		if (tag == null) {
			throw new BuildException("tag must be specified");
		}
		if (repositoryUrl == null) {
			throw new BuildException("repositoryUrl must be specified");
		}
		if (username == null || password == null) {
			throw new BuildException("username and password must be set");
		}

		// Verify that the given workspacePath is OK.
		verifyWorkspacePath();

		// Determine if the workspace can be switched. If so, get the URL.
		boolean switchOk = switchable();

		// Go ahead and try to switch or checkout the cache.
		updateCache(switchOk);
	}

	/**
	 * Do a series of checks to make sure that the given workspacePath is a
	 * writeable directory (possibly creating it).
	 */
	private void verifyWorkspacePath() {

		// First check that the path isn't null.
		if (wcPath == null) {
			throw new BuildException("workspacePath is not specified");
		}

		// If the workspace doesn't exist. Try to create the directory.
		if (!wcPath.exists()) {
			boolean ok = wcPath.mkdirs();
			if (!ok) {
				throw new BuildException("workspacePath could not be created: "
						+ wcPath);
			}
		}

		// Check that the workspacePath is actually a directory.
		if (!wcPath.isDirectory()) {
			throw new BuildException("workspacePath is not a directory: "
					+ wcPath);
		}

		// Check that we can write to the given directory.
		if (!wcPath.canWrite()) {
			throw new BuildException("cannot write to workspacePath: " + wcPath);
		}

	}

	/**
	 * Determine if the given workspacePath is an existing working copy and can
	 * be switched to a different URL.
	 * 
	 * @return workspace URL if can be switched
	 */
	private boolean switchable() {

		// Create status, copy, and WC (working copy) clients.
		SVNClientManager manager = SVNClientManager.newInstance();
		SVNWCClient wc = manager.getWCClient();

		// Retrieve the URL for the repository.
		String url = null;
		try {
			SVNInfo info = wc.doInfo(wcPath, SVNRevision.WORKING);
			SVNURL srcUrl = info.getURL();
			url = srcUrl.toString();
		} catch (SVNException consumed) {
		}

		// Check that the workspace URL is in the same repository that we want
		// to switch to.
		if (url != null) {
			if (!url.startsWith(repositoryUrl.toString())) {
				throw new BuildException("working space (" + wcPath
						+ ") is not in the correct repository ("
						+ repositoryUrl + ")");
			}
		}

		return (url != null);
	}

	private void updateCache(boolean switchOk) {

		// Create an update client to either switch the repository to checkout a
		// fresh copy.
		ISVNAuthenticationManager authManager = SVNWCUtil
				.createDefaultAuthenticationManager(username, password);
		SVNUpdateClient updater = new SVNUpdateClient(authManager, null);

		// Create the tag URL relative to the repository URL. Append "tags/" to
		// the tag, unless the tag name is trunk.
		SVNURL tagUrl = null;
		try {
			String tagname = ("trunk".equals(tag)) ? "trunk" : "tags/" + tag;
			tagUrl = repositoryUrl.appendPath(tagname, true);
		} catch (SVNException se) {
			throw new BuildException("can't create tag URL: " + se.getMessage());
		}

		try {
			if (switchOk) {
				System.out.println("Switching: \n    " + wcPath + "\n    "
						+ tagUrl.toString());
				updater.doSwitch(wcPath, tagUrl, SVNRevision.HEAD,
						SVNRevision.HEAD, SVNDepth.INFINITY, false, false);
			} else {
				System.out.println("Checking out: \n    " + wcPath + "\n    "
						+ tagUrl.toString());
				updater.doCheckout(tagUrl, wcPath, SVNRevision.HEAD,
						SVNRevision.HEAD, SVNDepth.INFINITY, false);
			}
		} catch (SVNException se) {
			if (!recover(updater, tagUrl)) {
				throw new BuildException("switch or checkout failed: " + tagUrl
						+ " " + wcPath);
			}
		}

	}

	/**
	 * Attempt to recover a failed switch or checkout.
	 * 
	 * @return true if the recovery was successful
	 */
	private boolean recover(SVNUpdateClient updater, SVNURL tagUrl) {

		// Get a working copy client to see what the current SVN url is.
		SVNClientManager manager = SVNClientManager.newInstance();
		SVNWCClient wc = manager.getWCClient();

		// Retrieve the URL for the repository.
		try {
			SVNInfo info = wc.doInfo(wcPath, SVNRevision.WORKING);
			SVNURL srcUrl = info.getURL();

			// If the repository URL isn't null and matches the tag URL, then
			// try to update the working copy.
			if (srcUrl.equals(tagUrl)) {
				int count = 1;
				while (count < recoveryLimit) {
					try {
						System.err.println("recovery attempt: " + count);
						updater.doUpdate(wcPath, SVNRevision.HEAD, SVNDepth.INFINITY, false, false);
						System.err.println("recovery successful");
						return true;
					} catch (SVNException consumed) {
						count++;
					}
				}
			} else {
				System.err.println("update to correct tag impossible");
			}

		} catch (SVNException consumed) {
		}

		return false;
	}

}
