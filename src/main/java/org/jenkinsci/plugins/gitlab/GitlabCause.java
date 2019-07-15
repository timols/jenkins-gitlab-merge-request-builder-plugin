package org.jenkinsci.plugins.gitlab;

import hudson.model.Cause;

import java.util.Map;

public class GitlabCause extends Cause {
    private final Integer mergeRequestId;
    private final Integer mergeRequestIid;
    private final String mergeRequestState;
    private final String author;
    private final String authorEmail;
    private final String sourceName;
    private final String sourceRepository;
    private final String sourceBranch;
    private final String targetBranch;
    private final Map<String, String> customParameters;
    private final String title;
    private final String description;
    private final Integer sourceProjectId;
    private final Integer targetProjectId;
    private final String lastCommitId;
    private final String webUrl;

    private GitlabBuildTrigger trigger;

    public GitlabCause(Integer mergeRequestId,
                       Integer mergeRequestIid,
                       String mergeRequestState,
                       String author,
                       String authorEmail,
                       String sourceName,
                       String sourceRepository,
                       String sourceBranch,
                       String targetBranch,
                       Map<String, String> customParameters,
                       String title,
                       String description,
                       Integer sourceProjectId,
                       Integer targetProjectId, 
                       String lastCommitId,
                       String webUrl) {
    	
        this.mergeRequestId = mergeRequestId;
        this.mergeRequestIid = mergeRequestIid;
        this.mergeRequestState = mergeRequestState;
        this.author = author;
        this.authorEmail = authorEmail;
        this.sourceName = sourceName;
        this.sourceRepository = sourceRepository;
        this.sourceBranch = sourceBranch;
        this.targetBranch = targetBranch;
        this.customParameters = customParameters;
        this.title = title;
        this.description = description;
        this.sourceProjectId = sourceProjectId;
        this.targetProjectId = targetProjectId;
        this.lastCommitId = lastCommitId;
        this.webUrl = webUrl;
    }
    @Override
    public String getShortDescription() {
        return "Gitlab Merge Request #" + mergeRequestIid + "(" + this.getAuthor() + ")" + " : " + sourceName + "/" + sourceBranch +
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

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Integer getSourceProjectId() {
		return sourceProjectId;
	}

    public Integer getTargetProjectId() {
		return targetProjectId;
	}

	public String getLastCommitId() {
        return lastCommitId;
    }

    public String getAuthor() {
        return author;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public String getWebUrl() {
        return webUrl;
    }

    public GitlabBuildTrigger getTrigger() {
        return trigger;
    }

    public void setTrigger(GitlabBuildTrigger trigger) {
        this.trigger = trigger;
    }

    public String getMergeRequestState() {
        return mergeRequestState;
    }
}
