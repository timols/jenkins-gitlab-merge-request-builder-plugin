package org.jenkinsci.plugins.gitlab;

import org.gitlab.api.GitlabAPI;

/**
 * GitlabAPI Wrapper Class
 */
public class Gitlab {
    private GitlabAPI _api;

    private void connect() {
        String privateToken = GitlabBuildTrigger.getDesc().getBotApiToken();
        String apiUrl = GitlabBuildTrigger.getDesc().getGitlabHostUrl();
        _api = GitlabAPI.connect(apiUrl, privateToken);
    }

    public GitlabAPI get() {
        if (_api == null) {
            connect();
        }

        return _api;
    }
}
