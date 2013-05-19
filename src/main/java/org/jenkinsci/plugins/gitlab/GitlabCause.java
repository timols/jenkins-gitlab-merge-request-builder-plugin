package org.jenkinsci.plugins.gitlab;

import hudson.model.Cause;

public class GitlabCause extends Cause {
    private final Integer _mergeRequestId;
    private final String _sourceBranch;
    private final String _targetBranch;

    public GitlabCause(Integer mergeRequestId, String sourceBranch, String targetBranch) {
        _mergeRequestId = mergeRequestId;
        _sourceBranch = sourceBranch;
        _targetBranch = targetBranch;
    }


    @Override
    public String getShortDescription() {
        return "Gitlab Merge Request #" + _mergeRequestId + " : " + _sourceBranch + " => " + _targetBranch;
    }

    public Integer getMergeRequestId() {
        return _mergeRequestId;
    }

    public String getSourceBranch() {
        return _sourceBranch;
    }

    public String getTargetBranch() {
        return _targetBranch;
    }
}
