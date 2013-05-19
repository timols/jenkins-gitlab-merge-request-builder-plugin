package org.jenkinsci.plugins.gitlab;

import java.util.Map;
import hudson.model.AbstractProject;

public class GitlabMergeRequestBuilder {

    private AbstractProject<?, ?> _project;
    private GitlabBuildTrigger _trigger;
    private Map<Integer, GitlabMergeRequestWrapper> _mergeRequests;
    private boolean _checked = false;
    private GitlabBuilds _builds;
    private GitlabRepository _repository;

    public static GitlabMergeRequestBuilder getBuilder() {
        return new GitlabMergeRequestBuilder();
    }

    private GitlabMergeRequestBuilder() {
    }

    public void stop() {
        _repository = null;
        _builds = null;
    }

    public void run() {
        if (_checked) {
            return;
        }

        _checked = true;
        _repository.check();
    }

    public GitlabMergeRequestBuilder setTrigger(GitlabBuildTrigger trigger) {
        _trigger = trigger;
        return this;
    }

    public GitlabMergeRequestBuilder setProject(AbstractProject<?, ?> project) {
        _project = project;

        return this;
    }

    public GitlabMergeRequestBuilder setMergeRequests(Map<Integer, GitlabMergeRequestWrapper> mergeRequests) {
        _mergeRequests = mergeRequests;
        return this;
    }

    public GitlabMergeRequestBuilder build() {
        if (_mergeRequests == null || _trigger == null || _project == null) {
            throw new IllegalStateException();
        }

        _repository = new GitlabRepository(_trigger.getProjectId(), this, _mergeRequests);
        _repository.init();
        _builds = new GitlabBuilds(_trigger, _repository);
        return this;
    }

    public GitlabBuilds getBuilds() {
        return _builds;
    }

    public Gitlab getGitlab() {
        if (_trigger != null) {
            return _trigger.getDescriptor().getGitlab();
        } else {
            return new Gitlab();
        }
    }
}
