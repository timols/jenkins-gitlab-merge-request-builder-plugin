package org.jenkinsci.plugins.gitlab;

import java.io.IOException;

import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabCommitStatus;
import org.gitlab.api.models.GitlabProject;

/**
 * GitlabAPI Wrapper Class
 */
public class Gitlab {

    private GitlabAPI api;

    private void connect() {
        String privateToken = GitlabBuildTrigger.getDesc().getBotApiTokenSecret().getPlainText();
        String apiUrl = GitlabBuildTrigger.getDesc().getGitlabHostUrl();
        
        api = GitlabAPI.connect(apiUrl, privateToken);
        
        api.ignoreCertificateErrors(GitlabBuildTrigger.getDesc().isIgnoreCertificateErrors());
    }

    public GitlabAPI get() {
        if (api == null) {
            connect();
        }

        return api;
    }
    
    public synchronized GitlabCommitStatus changeCommitStatus(Integer projectId, String branch, String commitHash, String commitStatus, String targetUrl) throws IOException {
    	GitlabProject project = get().getProject(projectId);
    	return get().createCommitStatus(project, commitHash, commitStatus, branch, "Jenkins", targetUrl, "Gitlab MR Builder");
    }
}
