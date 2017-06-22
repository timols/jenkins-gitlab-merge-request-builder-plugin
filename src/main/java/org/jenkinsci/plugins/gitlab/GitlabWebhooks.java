package org.jenkinsci.plugins.gitlab;

import com.google.gson.JsonSyntaxException;
import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import org.apache.commons.io.IOUtils;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabMergeRequest;
import org.gitlab.api.models.GitlabProject;
import org.jenkinsci.plugins.gitlab.models.webhook.MergeRequest;
import org.jenkinsci.plugins.gitlab.models.webhook.Note;
import org.jenkinsci.plugins.gitlab.models.webhook.OnlyType;
import org.kohsuke.stapler.*;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Created by lordx_000 on 11/14/2015.
 */
@Extension
public class GitlabWebhooks implements UnprotectedRootAction {

    private static final Logger LOGGER = Logger.getLogger(GitlabWebhooks.class.getName());
    private static ArrayList<GitlabBuildTrigger> triggers = new ArrayList<GitlabBuildTrigger>();
    public static final String URLNAME = "gitlab-webhook";

    /**
     * @param t
     */
    public static void addTrigger(GitlabBuildTrigger t) {
        triggers.add(t);
    }

    /**
     * @param m
     * @return
     */
    private static GitlabBuildTrigger findTrigger(MergeRequest m) {
        for (GitlabBuildTrigger t : triggers) {
            try {
                if (t.getProjectPath().equals(m.getTarget().path_with_namespace)) {
                    return t;
                }
            } catch (NullPointerException npe) {
                LOGGER.warning(String.format("%s where handle %s", npe.toString(), m.toString()));
            }
        }
        return null;
    }

    public HttpResponse doStart(StaplerRequest request) {
        HttpResponse response = new HttpResponse() {
            @Override
            public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
                rsp.getWriter().println("accepted");
            }
        };
        try {
            String requestBodyString = IOUtils.toString(request.getInputStream(), "UTF-8");
            LOGGER.fine(requestBodyString);
            OnlyType hookObjectKind = OnlyType.fromJson(requestBodyString);
            MergeRequest mergeRequest = null;
            switch (hookObjectKind.object_kind) {
                case "note":
                    Note mergeRequestNote = Note.fromJson(requestBodyString);
                    mergeRequest = mergeRequestNote.merge_request;
                    break;
                case "merge_request":
                    mergeRequest = MergeRequest.fromJson(requestBodyString);
                    break;
                default: {}
            }
            LOGGER.fine(String.format("MergeRequest is %s", mergeRequest));
            GitlabBuildTrigger trigger  = mergeRequest != null ? findTrigger(mergeRequest) : null;
            if (trigger != null) {
                GitlabCause cause = new GitlabCause(mergeRequest, new HashMap<String, String>());
                GitlabAPI api = trigger.getBuilder().getGitlab().get();
                GitlabProject project = api.getProject(cause.getTargetProjectId());
                GitlabMergeRequest gitlabMergeRequest = api.getMergeRequest(project, cause.getMergeRequestId());
                GitlabMergeRequestWrapper mergeRequestWrapper;
                GitlabMergeRequestBuilder currentBuilder = trigger.getBuilder();
                Map<Integer, GitlabMergeRequestWrapper> mergeRequestWrapperMap = currentBuilder.getMergeRequests();

                if (mergeRequestWrapperMap.containsKey(mergeRequest.getId())) {
                    mergeRequestWrapper = mergeRequestWrapperMap.get(mergeRequest.getId());
                } else {
                    mergeRequestWrapper = new GitlabMergeRequestWrapper(gitlabMergeRequest, currentBuilder, project);
                    mergeRequestWrapperMap.put(mergeRequest.getId(), mergeRequestWrapper);
                }
                mergeRequestWrapper.setLatestCommitOfMergeRequest(
                        mergeRequest.getId().toString(),
                        mergeRequest.getLast_commit().id);
                LOGGER.info(String.format("Webhook detected! Trying to build %s", mergeRequest.toString()));
                currentBuilder.getBuilds().build(cause, new HashMap<String, String>(), project, gitlabMergeRequest);
            } else {
                LOGGER.info(String.format("No suitable trigger found for MergeRequest %s! Skipping webhook", mergeRequest));
            }

        } catch (IOException ex) {
            LOGGER.severe("There was an error");
            LOGGER.throwing("GitlabWebhooks", "doStart", ex);
        } catch (JsonSyntaxException e) {
            LOGGER.warning(e.toString());
        } catch (Exception e) {
            LOGGER.severe(String.format("%s on run doStart", e));
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
        return URLNAME;
    }
}
