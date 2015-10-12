package org.jenkinsci.plugins.gitlab;

import org.gitlab.api.GitlabAPI;

/**
 * GitlabAPI Wrapper Class
 */
public class Gitlab {

    private GitlabAPI api;

    private void connect() {
        String privateToken = GitlabBuildTrigger.getDesc().getBotApiTokenSecret().getPlainText();
        String apiUrl = GitlabBuildTrigger.getDesc().getGitlabHostUrl();
        api = GitlabAPI.connect(apiUrl, privateToken);
    }

    public GitlabAPI get() {
        if (api == null) {
            connect();
        }

        return api;
    }
}
