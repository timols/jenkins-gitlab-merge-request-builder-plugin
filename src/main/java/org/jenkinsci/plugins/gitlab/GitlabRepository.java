package org.jenkinsci.plugins.gitlab;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.gitlab.api.models.GitlabMergeRequest;
import org.gitlab.api.models.GitlabNote;
import org.gitlab.api.models.GitlabProject;

public class GitlabRepository {

    private static final Logger _logger = Logger.getLogger(GitlabRepository.class.getName());
    private String _projectPath;

    private Map<Integer, GitlabMergeRequestWrapper> _mergeRequests;
    private GitlabProject _project;
    private GitlabMergeRequestBuilder _builder;

    public GitlabRepository(String projectPath, GitlabMergeRequestBuilder builder, Map<Integer, GitlabMergeRequestWrapper> mergeRequests) {
        _projectPath = projectPath;
        _builder = builder;
        _mergeRequests = mergeRequests;
    }

    public void init() {
        checkState();

        for (GitlabMergeRequestWrapper mergeRequestWrapper : _mergeRequests.values()) {
            mergeRequestWrapper.init(_builder, _project);
        }
    }

    private boolean checkState() {
        if (_project == null) {
            _project = getProjectForPath(_projectPath);
        }

        if (_project == null) {
            _logger.log(Level.SEVERE, "No project with path: " + _projectPath + " found");
        }

        return _project != null;
    }

    public void check() {
        if (!checkState()) {
            return;
        }

        List<GitlabMergeRequest> mergeRequests;
        try {
            mergeRequests = _builder.getGitlab().get().getOpenMergeRequests(_project);
        } catch (IOException e) {
            _logger.log(Level.SEVERE, "Could not retrieve merge requests.", e);
            return;
        }

        Set<Integer> closedMergedRequests = new HashSet<Integer>(_mergeRequests.keySet());

        for (GitlabMergeRequest mergeRequest : mergeRequests) {
            check(mergeRequest);
            closedMergedRequests.remove(mergeRequest.getId());
        }

        removeClosed(closedMergedRequests, _mergeRequests);
    }

    private void check(GitlabMergeRequest gitlabMergeRequest) {
        Integer id = gitlabMergeRequest.getId();
        GitlabMergeRequestWrapper mergeRequest;

        if (_mergeRequests.containsKey(id)) {
            mergeRequest = _mergeRequests.get(id);
        } else {
            mergeRequest = new GitlabMergeRequestWrapper(gitlabMergeRequest, _builder, _project);
            _mergeRequests.put(id, mergeRequest);
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
            List<GitlabProject> projects = _builder.getGitlab().get().getAllProjects();
            for (GitlabProject project : projects) {
                if (project.getPathWithNamespace().equals(path)) {
                    return project;
                }
            }
        } catch (IOException e) {
            _logger.log(Level.SEVERE, "Could not retrieve Project with path: " + path + " (Have you properly configured the project path?)");
        }
        return null;
    }

    public String getProjectUrl() {
        try {
            return _builder.getGitlab().get().getUrl(_project.getPathWithNamespace()).toString();
        } catch (IOException e) {
            return null;
        }
    }

    public String getMergeRequestUrl(Integer mergeRequestIid) {
        return getProjectUrl() + GitlabMergeRequest.URL + "/" + mergeRequestIid;
    }

    public GitlabNote createNote(Integer mergeRequestId, String message) {
        GitlabMergeRequestWrapper gitlabMergeRequestWrapper = _mergeRequests.get(mergeRequestId);
        return gitlabMergeRequestWrapper.createNote(message);
    }
}
