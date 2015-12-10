package org.jenkinsci.plugins.gitlab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import hudson.model.queue.QueueTaskFuture;
import hudson.util.IOUtils;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabMergeRequest;
import org.gitlab.api.models.GitlabProject;
import org.jenkinsci.plugins.gitlab.models.webhook.MergeRequest;
import org.jenkinsci.plugins.gitlab.models.webhook.OnlyType;
import org.kohsuke.stapler.*;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * Created by lordx_000 on 11/14/2015.
 */
@Extension
public class GitlabWebhooks implements UnprotectedRootAction {

    private static final Logger LOGGER = Logger.getLogger(GitlabWebhooks.class.getName());
    private static GitlabBuildTrigger trigger;

    private Gson g = new GsonBuilder().setPrettyPrinting().create();

    public static void setTrigger(GitlabBuildTrigger trigger) {
        GitlabWebhooks.trigger = trigger;
    }

    public HttpResponse doStart(StaplerRequest request) {

        HttpResponse response = new HttpResponse() {
            @Override
            public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
                rsp.getWriter().println("accepted");
            }
        };

        try {
            String theString = IOUtils.toString(request.getInputStream(), "UTF-8");

            OnlyType ot = g.fromJson(theString, OnlyType.class);

            LOGGER.fine(theString);
            LOGGER.fine(ot.object_kind);

            if (ot.object_kind.equals("merge_request")) {
                MergeRequest mergeRequest = g.fromJson(theString, MergeRequest.class);

                LOGGER.fine(mergeRequest.toString());

                if ("open".equals(mergeRequest.object_attributes.action) || "update".equals(mergeRequest.object_attributes.action)) {


                    if (trigger != null) {
                        GitlabCause cause = new GitlabCause(
                                mergeRequest.object_attributes.id,
                                mergeRequest.object_attributes.iid,
                                mergeRequest.object_attributes.source.name,
                                mergeRequest.object_attributes.source.http_url,
                                mergeRequest.object_attributes.source_branch,
                                mergeRequest.object_attributes.target_branch,
                                new HashMap<String, String>(),
                                "",
                                mergeRequest.object_attributes.source_project_id,
                                mergeRequest.object_attributes.last_commit.id);

                        GitlabMergeRequestWrapper mergeRequestWrapper = trigger.getBuilder().getMergeRequests().get(mergeRequest.object_attributes.id);
                        mergeRequestWrapper.setLatestCommitOfMergeRequest(
                                mergeRequest.object_attributes.id.toString(),
                                mergeRequest.object_attributes.last_commit.id);


                        GitlabAPI api = trigger.getBuilder().getGitlab().get();
                        GitlabProject project = api.getProject(cause.getSourceProjectId());
                        GitlabMergeRequest gitlabMergeRequest = api.getMergeRequest(project, cause.getMergeRequestId());
                        
                        trigger.getBuilder().getBuilds().build(cause, new HashMap<String, String>(), project, gitlabMergeRequest);
                    } else {
                        LOGGER.severe("TRIGGER is not set.");
                    }
                }
            }

        } catch (Exception ex) {
            LOGGER.severe("There was an error");
            LOGGER.throwing("GitlabWebhooks", "doStart", ex);
        }

        return response;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return "gitlab-webhook";
    }
}
