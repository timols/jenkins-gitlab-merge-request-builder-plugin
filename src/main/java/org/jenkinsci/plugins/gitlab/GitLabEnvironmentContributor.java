package org.jenkinsci.plugins.gitlab;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;

@Extension
public class GitLabEnvironmentContributor extends EnvironmentContributor {
    @Override
    public void buildEnvironmentFor(@Nonnull Run r, @Nonnull EnvVars envs,
        @Nonnull TaskListener listener) throws IOException, InterruptedException {
        GitlabCause cause = (GitlabCause) r.getCause(GitlabCause.class);
        if (cause != null) {
            Map<String, String> variables = new HashMap<>();
            variables.put("gitlabMergeRequestId", cause.getMergeRequestId() + "");
            variables.put("gitlabMergeRequestIid", cause.getMergeRequestIid() + "");
            variables.put("gitlabMergeRequestAuthor", cause.getAuthor());
            variables.put("gitlabMergeRequestAuthorEmail", cause.getAuthorEmail());
            variables.put("gitlabSourceName", cause.getSourceName());
            variables.put("gitlabSourceRepository", cause.getSourceRepository());
            variables.put("gitlabSourceBranch", cause.getSourceBranch());
            variables.put("gitlabTargetBranch", cause.getTargetBranch());
            variables.put("gitlabTitle", cause.getTitle());
            variables.put("gitlabDescription", cause.getDescription());
            variables.put("gitlabSourceProjectId", cause.getSourceProjectId()+"");
            variables.put("gitlabTargetProjectId", cause.getTargetProjectId()+"");
            variables.put("gitlabLastCommitId", cause.getLastCommitId());
            envs.overrideAll(variables);
        }
    }
}
