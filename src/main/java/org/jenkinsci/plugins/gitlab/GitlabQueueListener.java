package org.jenkinsci.plugins.gitlab;

import java.io.IOException;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import hudson.Extension;
import hudson.model.Cause;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;

@Extension
public class GitlabQueueListener extends QueueListener {
	
	private static final Logger LOGGER = Logger.getLogger(GitlabQueueListener.class.getName());
	
	public void onEnterWaiting(Queue.WaitingItem wi) {
		for (Cause c : wi.getCauses()) {
			if (c instanceof GitlabCause) {
				Gitlab gitlab = new Gitlab();
				GitlabCause cause = (GitlabCause) c;
				
				try {
					String url = Jenkins.getInstance().getRootUrl() + wi.getUrl();
					gitlab.changeCommitStatus(cause.getTargetProjectId(), cause.getSourceBranch(), cause.getLastCommitId(), "pending", url);
				} catch (IOException e) {
					LOGGER.info("error trying to set pending status");
				}
			}
		}
	}

}
