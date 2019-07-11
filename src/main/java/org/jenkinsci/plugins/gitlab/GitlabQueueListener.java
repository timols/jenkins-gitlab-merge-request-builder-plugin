package org.jenkinsci.plugins.gitlab;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import hudson.Extension;
import hudson.model.Cause;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;

@Extension
public class GitlabQueueListener extends QueueListener {
	
	private static final Logger LOGGER = Logger.getLogger(GitlabQueueListener.class.getName());
	
	public void onEnterBuildable(Queue.BuildableItem bi) {
		for (Cause c : bi.getCauses()) {
			if (c instanceof GitlabCause) {
				Gitlab gitlab = GitlabBuildTrigger.DESCRIPTOR.getGitlab();
				GitlabCause cause = (GitlabCause) c;
				Jenkins instance = Jenkins.getInstance();
				String rootUrl = instance == null ? "/" : instance.getRootUrl();
				String url = rootUrl + bi.getUrl();
				try {
					LOGGER.log(Level.INFO, "Send pending status to commit: " + cause.getLastCommitId());
					gitlab.changeCommitStatus(cause.getTargetProjectId(), cause.getSourceBranch(), cause.getLastCommitId(), "pending", url);
				} catch (IOException e) {
					LOGGER.info("error trying to set pending status");
				}
			}
		}
	}

}
