package org.jenkinsci.plugins.gitlab;

import java.util.Map;

import hudson.model.Cause;

public class GitlabCause extends Cause {
    private final Integer _mergeRequestId;
    private final Integer _mergeRequestIid;
    private final String _sourceName;
    private final String _sourceRepository;
    private final String _sourceBranch;
    private final String _targetBranch;
	private final Map<String, String> _customParameters;

    public GitlabCause(Integer mergeRequestId, Integer mergeRequestIid, String sourceName,
            String sourceRepository, String sourceBranch, String targetBranch, Map<String, String> customParameters) {
        _mergeRequestId = mergeRequestId;
        _mergeRequestIid = mergeRequestIid;
        _sourceName = sourceName;
        _sourceRepository = sourceRepository;
        _sourceBranch = sourceBranch;
        _targetBranch = targetBranch;
		_customParameters = customParameters;
    }


    @Override
    public String getShortDescription() {
        return "Gitlab Merge Request #" + _mergeRequestIid + " : " + _sourceName + "/" + _sourceBranch +
            " => " + _targetBranch;
    }

    public Integer getMergeRequestId() {
        return _mergeRequestId;
    }

    public Integer getMergeRequestIid() {
        return _mergeRequestIid;
    }

    public String getSourceName() {
        return _sourceName;
    }

    public String getSourceRepository() {
        return _sourceRepository;
    }

    public String getSourceBranch() {
        return _sourceBranch;
    }

    public String getTargetBranch() {
        return _targetBranch;
    }
    
    public Map<String, String> getCustomParameters() {
		return _customParameters;
	}
}
