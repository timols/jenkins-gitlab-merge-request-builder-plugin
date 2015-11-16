package org.jenkinsci.plugins.gitlab;

import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabCommitStatus;
import org.gitlab.api.models.GitlabMergeRequest;
import org.gitlab.api.models.GitlabProject;

public class GitlabBuilds {

    private static final Logger LOGGER = Logger.getLogger(GitlabBuilds.class.getName());

    private GitlabBuildTrigger trigger;
    private GitlabRepository repository;

    public GitlabBuilds(GitlabBuildTrigger trigger, GitlabRepository repository) {
        this.trigger = trigger;
        this.repository = repository;
    }

    public String build(GitlabCause cause, Map<String, String> customParameters) throws IOException {
    	
    	boolean shouldRun = true;
    	GitlabAPI api = trigger.getBuilder().getGitlab().get();
    	GitlabProject project = api.getProject(cause.getSourceProjectId());
    	GitlabMergeRequest mergeRequest = api.getMergeRequest(project, cause.getMergeRequestId());
    	
    	if (isAllowedByTargetBranchRegex(cause.getTargetBranch())) {
            LOGGER.log(Level.INFO, "The target regex matches the target branch {" + cause.getTargetBranch() + "}. Source branch {" + cause.getSourceBranch() + "}");
            shouldRun = true;
        } else {
            LOGGER.log(Level.INFO, "The target regex did not match the target branch {" + cause.getTargetBranch() + "}. Not triggering this job. Source branch {" + cause.getSourceBranch() + "}");
            shouldRun = false;
        }
    	
    	if (hasCommitStatus(project, cause.getLastCommitId(), api)) {
        	shouldRun = false;
        }
    	
    	if (shouldRun) {
    		String assigneeFilter = trigger.getAssigneeFilter();
    		
    		if (!"".equals(assigneeFilter)) {
    			shouldRun = filterMatch(assigneeFilter, mergeRequest.getAssignee().getUsername(), "Assignee");
    		}
        }
    	
    	if (shouldRun) {
    		String tagFilter = trigger.getTagFilter();
    		
    		if (!"".equals(tagFilter)) {
    			shouldRun = filterMatch(tagFilter, mergeRequest.getLabels(), "Assignee");
    		}
        }
    	
    	if (shouldRun == true) {
    		LOGGER.info("Build is supposed to run");
    		
	    	QueueTaskFuture<?> build = trigger.startJob(cause);
	        if (build == null) {
	            LOGGER.log(Level.SEVERE, "Job failed to start.");
	        }
	        return withCustomParameters(new StringBuilder("Build triggered."), customParameters).toString();
    	} else {
    		LOGGER.info("Build is not supposed to run");
    		return "";
    	}
    }
    
    /**
     * Check whether the branchName can be matched using the target branch
     * regex. Empty regex patterns will cause this method to return true.
     *
     * @param branchName
     * @return true when the name can be matched or when the regex is empty.
     * Otherwise false.
     */
    public boolean isAllowedByTargetBranchRegex(String branchName) {
        String regex = trigger.getTargetBranchRegex();
        // Allow when no pattern has been specified. (default behavior)
        if (StringUtils.isEmpty(regex)) {
            return true;
        }
        Pattern pattern = Pattern.compile(regex);
        return pattern.matcher(branchName).matches();
    }
    
    /**
     * 
     * synchronized so that there can't be a race condition here.
     * 
     * @param project
     * @param commitHash
     * @param api
     * @return
     */
    private synchronized boolean hasCommitStatus(GitlabProject project, String commitHash, GitlabAPI api) {
    	try {
    		List<GitlabCommitStatus> statuses = api.getCommitStatuses(project, commitHash);
    		
    		for (GitlabCommitStatus status : statuses) {
    			LOGGER.fine("Status of " + commitHash + " -> " + status.getStatus());
    		}
    		
    		return true;
		} catch (IOException ex) {
			LOGGER.throwing("GitlabMergeRequestWrapper", "checkStatus", ex);
		}
    	
    	
    	return false;
    }
    
    private boolean filterMatch(String filter, String target, String type) {
        boolean shouldRun = true;
        if ("".equals(filter)) {
            shouldRun = true;
        } else {
            if (filter.equals(target)) {
                shouldRun = true;
            } else {
                shouldRun = false;
                LOGGER.info(type + ": " + target + " does not match " + filter);
            }
        }
        return shouldRun;
    }
    
    private boolean filterMatch(String filter, String target[], String type) {
        boolean shouldRun = false;
        if ("".equals(filter)) {
            shouldRun = true;
        } else {
        	
        	for (String test : target) {
        		if (filter.equals(target)) {
                    shouldRun = true;
                }
        	}
        	
            if (!shouldRun) {
                LOGGER.info(type + ": " + target + " does not match " + filter);
            }
        }
        return shouldRun;
    }

    private StringBuilder withCustomParameters(StringBuilder sb, Map<String, String> customParameters) {
        if (customParameters.isEmpty()) {
            return sb;
        }

        sb.append("\n\nUsing custom parameters:");
        for (Map.Entry<String, String> entry : customParameters.entrySet()) {
            sb.append("\n* `");
            sb.append(entry.getKey());
            sb.append("`=`");
            sb.append(entry.getValue());
            sb.append("`");
        }
        sb.append("\n\n");
        return sb;
    }

    private GitlabCause getCause(AbstractBuild build) {
        Cause cause = build.getCause(GitlabCause.class);

        if (cause == null || !(cause instanceof GitlabCause)) {
            return null;
        }

        return (GitlabCause) cause;
    }


    public void onStarted(AbstractBuild build) {
        GitlabCause cause = getCause(build);

        if (cause == null) {
            return;
        }

        try {
            build.setDescription("<a href=\"" + repository.getMergeRequestUrl(cause.getMergeRequestIid()) + "\">" + getOnStartedMessage(cause) + "</a>");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Can't update build description", e);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Build Started: ");
        String url = Jenkins.getInstance().getRootUrl() + build.getUrl();
        sb.append("[").append(url).append("](").append(url).append(")");
        repository.createNote(cause.getMergeRequestId(), sb.toString(), false, false);

        if (trigger.getDescriptor().isUpdateCommitStatus()) {
            repository.changeCommitStatus(cause.getMergeRequestId(), cause.getLastCommitId(), "running", url);
        }
    }

    public void onCompleted(AbstractBuild build) {
        GitlabCause cause = getCause(build);

        if (cause == null) {
            return;
        }

        boolean stable = false;
        StringBuilder stringBuilder = new StringBuilder();
        if (build.getResult() == Result.SUCCESS) {
            stable = true;
            if (build.getBuildVariables().containsKey("gitlabSuccessMessage")) {
                stringBuilder.append(build.getBuildVariables().get("gitlabSuccessMessage"));
            } else {
                stringBuilder.append(trigger.getDescriptor().getSuccessMessage());
            }
        } else if (build.getResult() == Result.UNSTABLE) {
            if (build.getBuildVariables().containsKey("gitlabUnstableMessage")) {
                stringBuilder.append(build.getBuildVariables().get("gitlabUnstableMessage"));
            } else {
                stringBuilder.append(trigger.getDescriptor().getUnstableMessage());
            }
        } else {
            if (build.getBuildVariables().containsKey("gitlabFailureMessage")) {
                stringBuilder.append(build.getBuildVariables().get("gitlabFailureMessage"));
            } else {
                stringBuilder.append(trigger.getDescriptor().getFailureMessage());
            }
        }

        if (!trigger.getBuilder().isEnableBuildTriggeredMessage()) {
            withCustomParameters(stringBuilder, cause.getCustomParameters());
        }

        String buildUrl = Jenkins.getInstance().getRootUrl() + build.getUrl();
        stringBuilder.append("\nBuild results available at: ")
                .append("[").append(buildUrl).append("](").append(buildUrl).append(")"); // Link in markdown format

        boolean shouldClose = false;
        if (!stable && trigger.getAutoCloseFailed()) {
            shouldClose = true;
        }

        boolean shouldMerge = false;
        if (stable && trigger.getAutoMergePassed()) {
            shouldMerge = true;
        }

        repository.createNote(cause.getMergeRequestId(), stringBuilder.toString(), shouldClose, shouldMerge);

        if (trigger.getDescriptor().isUpdateCommitStatus()) {
            String status = (build.getResult() == Result.SUCCESS) ? "success" : "failed";
            repository.changeCommitStatus(cause.getMergeRequestId(), cause.getLastCommitId(), status, buildUrl);
        }
    }

    private String getOnStartedMessage(GitlabCause cause) {
        return "Merge Request #" + cause.getMergeRequestIid() + " (" + cause.getSourceBranch() + " => " + cause.getTargetBranch() + ")";
    }
}
