package org.jenkinsci.plugins.gitlab;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import jenkins.model.Jenkins;

public class GitlabBuilds {
    private static final Logger _logger = Logger.getLogger(GitlabBuilds.class.getName());
    private GitlabBuildTrigger _trigger;
    private GitlabRepository _repository;

    public GitlabBuilds(GitlabBuildTrigger trigger, GitlabRepository repository) {
        _trigger = trigger;
        _repository = repository;
    }

    public String build(GitlabMergeRequestWrapper mergeRequest) {
        GitlabCause cause = new GitlabCause(mergeRequest.getId(), mergeRequest.getIid(), mergeRequest.getSource(), mergeRequest.getTarget());

        QueueTaskFuture<?> build = _trigger.startJob(cause);
        if (build == null) {
            _logger.log(Level.SEVERE, "Job failed to start.");
        }
        return "Build triggered.";
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
            build.setDescription("<a href=\"" + _repository.getMergeRequestUrl(cause.getMergeRequestIid()) + "\">" + getOnStartedMessage(cause) + "</a>");
        } catch (IOException e) {
            _logger.log(Level.SEVERE, "Can't update build description", e);
        }
    }

    public void onCompleted(AbstractBuild build) {
        GitlabCause cause = getCause(build);

        if (cause == null) {
            return;
        }

        StringBuilder stringBuilder = new StringBuilder();
        if (build.getResult() == Result.SUCCESS) {
            stringBuilder.append(_trigger.getDescriptor().getSuccessMessage());
        } else if (build.getResult() == Result.UNSTABLE) {
            stringBuilder.append(_trigger.getDescriptor().getUnstableMessage());
        } else {
            stringBuilder.append(_trigger.getDescriptor().getFailureMessage());
        }

        String buildUrl = Jenkins.getInstance().getRootUrl() + build.getUrl();
        stringBuilder.append("\nBuild results available at: ").append(buildUrl);
        _repository.createNote(cause.getMergeRequestId(), stringBuilder.toString());
    }

    private String getOnStartedMessage(GitlabCause cause) {
        return "Merge Request #" + cause.getMergeRequestIid() + " (" + cause.getSourceBranch() + " => " + cause.getTargetBranch() + ")";
    }
}
