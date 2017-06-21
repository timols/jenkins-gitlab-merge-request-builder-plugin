package org.jenkinsci.plugins.gitlab;

import hudson.model.Cause;
import org.jenkinsci.plugins.gitlab.models.webhook.MergeRequest;

import java.util.HashMap;
import java.util.Map;

public class GitlabCause extends Cause {
    private final Integer mergeRequestId;
    private final Integer mergeRequestIid;
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

    public GitlabCause(Integer mergeRequestId,
                       Integer mergeRequestIid,
                       String sourceName,
                       String sourceRepository,
                       String sourceBranch,
                       String targetBranch,
                       Map<String, String> customParameters,
                       String title,
                       String description,
                       Integer sourceProjectId,
                       Integer targetProjectId, 
                       String lastCommitId) {
    	
        this.mergeRequestId = mergeRequestId;
        this.mergeRequestIid = mergeRequestIid;
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
    }
    public GitlabCause(MergeRequest m, HashMap<String, String> params) {
        this.mergeRequestId = m.getId();
        this.mergeRequestIid = m.getIid();
        this.sourceName = m.getSource().name;
        this.sourceRepository = m.getSource().http_url;
        this.sourceBranch = m.getSource_branch();
        this.targetBranch = m.getTarget_branch();
        this.customParameters = params;
        this.title = m.getTitle();
        this.description = m.getDescription();
        this.sourceProjectId = m.getSource_project_id();
        this.targetProjectId = m.getTarget_project_id();
        this.lastCommitId = m.getLast_commit().id;
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

//    public static GitlabCause fromMergeRequest(MergeRequest mergeRequest) {
//
//    }
}
