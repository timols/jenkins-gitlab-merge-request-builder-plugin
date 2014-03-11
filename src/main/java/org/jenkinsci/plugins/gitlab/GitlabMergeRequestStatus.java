package org.jenkinsci.plugins.gitlab;

import java.util.HashMap;

public class GitlabMergeRequestStatus {
    private HashMap<String, String> mergeRequestStatus;

    protected GitlabMergeRequestStatus() {
    	mergeRequestStatus = new HashMap<String, String>();
    }

    public String getLatestCommitOfMergeRequest(String _requestId) {
        if (mergeRequestStatus.containsKey(_requestId)) {
            return mergeRequestStatus.get(_requestId);
        }
        return null;
    }

    public void setLatestCommitOfMergeRequest(String _requestId,
            String latestCommit) {
        mergeRequestStatus.put(_requestId, latestCommit);
    }

}
