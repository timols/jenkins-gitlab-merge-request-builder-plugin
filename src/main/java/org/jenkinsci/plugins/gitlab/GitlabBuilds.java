package org.jenkinsci.plugins.gitlab;

import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GitlabBuilds {

    private static final Logger LOGGER = Logger.getLogger(GitlabBuilds.class.getName());

    private GitlabBuildTrigger trigger;
    private GitlabRepository repository;

    public GitlabBuilds(GitlabBuildTrigger trigger, GitlabRepository repository) {
        this.trigger = trigger;
        this.repository = repository;
    }

    public String build(GitlabMergeRequestWrapper mergeRequest, Map<String, String> customParameters) {
        GitlabCause cause = new GitlabCause(mergeRequest.getId(), mergeRequest.getIid(),
                mergeRequest.getSourceName(), mergeRequest.getSourceRepository(),
                mergeRequest.getSourceBranch(), mergeRequest.getTargetBranch(), customParameters, mergeRequest.getDescription());

        QueueTaskFuture<?> build = trigger.startJob(cause);
        if (build == null) {
            LOGGER.log(Level.SEVERE, "Job failed to start.");
        }
        return withCustomParameters(new StringBuilder("Build triggered."), customParameters).toString();
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
    }

    private String getOnStartedMessage(GitlabCause cause) {
        return cause.getShortDescription();
    }
}
