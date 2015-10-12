package org.jenkinsci.plugins.gitlab;

import hudson.model.Cause;

import java.util.Map;

public class GitlabCause extends Cause {
    private final Integer mergeRequestId;
    private final Integer mergeRequestIid;
    private final String sourceName;
    private final String sourceRepository;
    private final String sourceBranch;
    private final String targetBranch;
    private final Map<String, String> customParameters;
    private final String description;

    public GitlabCause(Integer mergeRequestId,
                       Integer mergeRequestIid,
                       String sourceName,
                       String sourceRepository,
                       String sourceBranch,
                       String targetBranch,
                       Map<String, String> customParameters,
                       String description) {
        this.mergeRequestId = mergeRequestId;
        this.mergeRequestIid = mergeRequestIid;
        this.sourceName = sourceName;
        this.sourceRepository = sourceRepository;
        this.sourceBranch = sourceBranch;
        this.targetBranch = targetBranch;
        this.customParameters = customParameters;
        this.description = description;
    }


    @Override
    public String getShortDescription() {
        return "Gitlab Merge Request #" + mergeRequestIid + " : " + sourceName + "/" + sourceBranch +
                " => " + targetBranch;
    }

    public Integer getMergeRequestId() {
        return mergeRequestId;
    }

    public Integer getMergeRequestIid() {
        return mergeRequestIid;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getSourceRepository() {
        return sourceRepository;
    }

    public String getSourceBranch() {
        return sourceBranch;
    }

    public String getTargetBranch() {
        return targetBranch;
    }

    public Map<String, String> getCustomParameters() {
        return customParameters;
    }

    public String getDescription() {
        return description;
    }

}
