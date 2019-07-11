package org.jenkinsci.plugins.gitlab;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import jenkins.model.Jenkins;
import org.gitlab.api.models.GitlabMergeRequest;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class GitlabBuildListener extends RunListener<Run<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(GitlabBuildListener.class.getName());

    private String getRootUrl() {
        Jenkins instance = Jenkins.getInstance();
        return instance == null ? "" : instance.getRootUrl();
    }

    @Override
    public void onStarted(Run<?, ?> build, TaskListener listener) {
        GitlabCause gitlabCause = build.getCause(GitlabCause.class);
        Gitlab gitlab = GitlabBuildTrigger.DESCRIPTOR.getGitlab();
        try {
            build.setDescription("<a href=\"" + gitlabCause.getWebUrl() + "\">" + gitlabCause.getShortDescription() + "</a>");
            String url = getRootUrl() + build.getUrl();

            LOGGER.log(Level.INFO, "Send running status to commit: " + gitlabCause.getLastCommitId() + ", url:" + url);
            gitlab.changeCommitStatus(gitlabCause.getTargetProjectId(), gitlabCause.getSourceBranch(), gitlabCause.getLastCommitId(), "running", url);

            if (gitlabCause.getTrigger().getPublishBuildProgressMessages()) {
                GitlabMergeRequestWrapper.createNote(gitlabCause.getMergeRequestId(),
                        gitlabCause.getMergeRequestIid(),
                        gitlabCause.getSourceProjectId(), "Build Started: " + "[" + url + "](" + url + ")", false, false);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Can't update build description", e);
        }
    }

    @Override
    public void onCompleted(Run<?, ?> build, TaskListener listener) {
        GitlabCause gitlabCause = build.getCause(GitlabCause.class);
        Gitlab gitlab = GitlabBuildTrigger.DESCRIPTOR.getGitlab();
        try {
            boolean stable = false;
            StringBuilder stringBuilder = new StringBuilder();
            Result result = build.getResult();
            EnvVars envVars = build.getEnvironment(listener);
            if (result == Result.SUCCESS) {
                stable = true;
                if (envVars.containsKey("gitlabSuccessMessage")) {
                    stringBuilder.append(envVars.get("gitlabSuccessMessage"));
                } else {
                    stringBuilder.append(GitlabBuildTrigger.DESCRIPTOR.getSuccessMessage());
                }
            } else if (result == Result.UNSTABLE) {
                if (envVars.containsKey("gitlabUnstableMessage")) {
                    stringBuilder.append(envVars.get("gitlabUnstableMessage"));
                } else {
                    stringBuilder.append(GitlabBuildTrigger.DESCRIPTOR.getUnstableMessage());
                }
            } else {
                if (envVars.containsKey("gitlabFailureMessage")) {
                    stringBuilder.append(envVars.get("gitlabFailureMessage"));
                } else {
                    stringBuilder.append(GitlabBuildTrigger.DESCRIPTOR.getFailureMessage());
                }
            }

            if (!gitlabCause.getTrigger().getDescriptor().isEnableBuildTriggeredMessage()) {
               GitlabBuilds.withCustomParameters(stringBuilder, gitlabCause.getCustomParameters());
            }

            String buildUrl = getRootUrl() + build.getUrl();
            stringBuilder
                    .append("\nBuild results available at: ")
                    .append("[")
                    .append(buildUrl)
                    .append("](")
                    .append(buildUrl)
                    .append(")"); // Link in markdown format

            boolean shouldClose = false;
            if (!stable && gitlabCause.getTrigger().getAutoCloseFailed()) {
                shouldClose = true;
            }

            boolean shouldMerge = false;
            if (stable && gitlabCause.getTrigger().getAutoMergePassed()) {
                shouldMerge = true;
            }

            if (gitlabCause.getTrigger().getPublishBuildProgressMessages() || !stable) {
                GitlabMergeRequestWrapper.createNote(gitlabCause.getMergeRequestId(),
                        gitlabCause.getMergeRequestIid(),
                        gitlabCause.getSourceProjectId(),
                        stringBuilder.toString(),
                        shouldClose,
                        shouldMerge);
            }

            String status = (result == Result.SUCCESS) ? "success" : ((result == Result.ABORTED) ? "canceled" :  "failed");
            LOGGER.log(Level.INFO, "Send " + status + " status to commit: " + gitlabCause.getLastCommitId() + ", url:" + buildUrl);
            gitlab.changeCommitStatus(gitlabCause.getTargetProjectId(), gitlabCause.getSourceBranch(), gitlabCause.getLastCommitId(), status, buildUrl);
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Can't update build description", e);
        }
    }
}
