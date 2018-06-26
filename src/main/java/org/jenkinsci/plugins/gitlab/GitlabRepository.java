package org.jenkinsci.plugins.gitlab;

import org.gitlab.api.models.GitlabCommitStatus;
import org.gitlab.api.models.GitlabMergeRequest;
import org.gitlab.api.models.GitlabNote;
import org.gitlab.api.models.GitlabProject;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GitlabRepository {

    private static final Logger LOGGER = Logger.getLogger(GitlabRepository.class.getName());
    private String projectPath;

    private Map<Integer, GitlabMergeRequestWrapper> mergeRequests;
    private GitlabProject project;
    private GitlabMergeRequestBuilder builder;

    public GitlabRepository(String projectPath, GitlabMergeRequestBuilder builder, Map<Integer, GitlabMergeRequestWrapper> mergeRequests) {
        this.projectPath = projectPath;
        this.builder = builder;
        this.mergeRequests = mergeRequests;
    }

    public void init() {
        checkState();

        for (GitlabMergeRequestWrapper mergeRequestWrapper : mergeRequests.values()) {
            mergeRequestWrapper.init(builder, project);
        }
    }

    private boolean checkState() {
        if (project == null) {
            project = getProjectForPath(projectPath);
        }

        if (project == null) {
            LOGGER.log(Level.SEVERE, "No project with path: " + projectPath + " found");
        }

        return project != null;
    }

    public void check() {
        if (!checkState()) {
            return;
        }

        List<GitlabMergeRequest> mergeRequests;
        try {
            mergeRequests = builder.getGitlab().get().getOpenMergeRequests(project);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not retrieve merge requests.", e);
            return;
        }

        Set<Integer> closedMergedRequests = new HashSet<Integer>(this.mergeRequests.keySet());

        for (GitlabMergeRequest mergeRequest : mergeRequests) {
            check(mergeRequest);
            closedMergedRequests.remove(mergeRequest.getId());
        }

        removeClosed(closedMergedRequests, this.mergeRequests);
    }

    private void check(GitlabMergeRequest gitlabMergeRequest) {
        Integer id = gitlabMergeRequest.getId();
        GitlabMergeRequestWrapper mergeRequest;

        if (mergeRequests.containsKey(id)) {
            mergeRequest = mergeRequests.get(id);
        } else {
            mergeRequest = new GitlabMergeRequestWrapper(gitlabMergeRequest, builder, project);
            mergeRequests.put(id, mergeRequest);
        }

        mergeRequest.check(gitlabMergeRequest);
    }

    private void removeClosed(Set<Integer> closedMergeRequests, Map<Integer, GitlabMergeRequestWrapper> mergeRequests) {
        if (closedMergeRequests.isEmpty()) {
            return;
        }

        for (Integer id : closedMergeRequests) {
            mergeRequests.remove(id);
        }
    }

    private GitlabProject getProjectForPath(String path) {
        try {
            List<GitlabProject> projects = builder.getGitlab().get().getProjects();
            for (GitlabProject project : projects) {
                if (project.getPathWithNamespace().equals(path)) {
                    return project;
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not retrieve Project with path: " + path + " (Have you properly configured the project path?)");
        }
        return null;
    }

    public String getProjectUrl() {
        try {
            return builder.getGitlab().get().getUrl(project.getPathWithNamespace()).toString();
        } catch (IOException e) {
            return null;
        }
    }

    public String getMergeRequestUrl(Integer mergeRequestIid) {
        return getProjectUrl() + GitlabMergeRequest.URL + "/" + mergeRequestIid;
    }

    public GitlabNote createNote(Integer mergeRequestId, String message, boolean shouldClose, boolean shouldMerge) {
        GitlabMergeRequestWrapper gitlabMergeRequestWrapper = mergeRequests.get(mergeRequestId);
        return gitlabMergeRequestWrapper.createNote(message, shouldClose, shouldMerge);
    }

    public GitlabCommitStatus changeCommitStatus(Integer mergeRequestId, String commitHash, String commitStatus, String targetUrl) {
        if (commitHash != null) {

            GitlabMergeRequestWrapper gitlabMergeRequestWrapper = mergeRequests.get(mergeRequestId);

            LOGGER.info("Sending Status: " + commitStatus);

            return gitlabMergeRequestWrapper.changeCommitStatus(commitHash, commitStatus, targetUrl);
        } else {
            return null;
        }
    }
}
