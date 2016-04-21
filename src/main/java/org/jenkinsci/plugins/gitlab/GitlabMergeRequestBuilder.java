package org.jenkinsci.plugins.gitlab;

import hudson.model.AbstractProject;

import java.util.Map;

public class GitlabMergeRequestBuilder {

    private AbstractProject<?, ?> project;
    private GitlabBuildTrigger trigger;
    private Map<Integer, GitlabMergeRequestWrapper> mergeRequests;
    private GitlabBuilds builds;
    private GitlabRepository repository;

    public static GitlabMergeRequestBuilder getBuilder() {
        return new GitlabMergeRequestBuilder();
    }

    private GitlabMergeRequestBuilder() {
    }

    public void stop() {
        repository = null;
        builds = null;
    }

    public void run() {
        repository.check();
    }

    public GitlabMergeRequestBuilder setTrigger(GitlabBuildTrigger trigger) {
        this.trigger = trigger;
        return this;
    }

    public GitlabBuildTrigger getTrigger() {
        return trigger;
    }

    public GitlabMergeRequestBuilder setProject(AbstractProject<?, ?> project) {
        this.project = project;
        return this;
    }

    public GitlabMergeRequestBuilder setMergeRequests(Map<Integer, GitlabMergeRequestWrapper> mergeRequests) {
        this.mergeRequests = mergeRequests;
        return this;
    }

    public Map<Integer, GitlabMergeRequestWrapper> getMergeRequests() {
        return mergeRequests;
    }

    public GitlabMergeRequestBuilder build() {
        if (mergeRequests == null || trigger == null || project == null) {
            throw new IllegalStateException();
        }

        repository = new GitlabRepository(trigger.getProjectPath(), this, mergeRequests);
        repository.init();
        builds = new GitlabBuilds(trigger, repository);
        return this;
    }

    public GitlabBuilds getBuilds() {
        return builds;
    }

    public Gitlab getGitlab() {
        return GitlabBuildTrigger.DESCRIPTOR.getGitlab();
    }

    public boolean isEnableBuildTriggeredMessage() {
        if (trigger != null) {
            return trigger.getDescriptor().isEnableBuildTriggeredMessage();
        } else {
            return GitlabBuildTrigger.DESCRIPTOR.isEnableBuildTriggeredMessage();
        }
    }

    public boolean isPublishBuildProgressMessages() {
        if (trigger != null) {
            return trigger.getDescriptor().isPublishBuildProgressMessages();
        } else {
            return GitlabBuildTrigger.DESCRIPTOR.isPublishBuildProgressMessages();
        }
    }

}
