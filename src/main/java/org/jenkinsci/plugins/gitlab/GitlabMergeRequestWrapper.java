package org.jenkinsci.plugins.gitlab;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabCommit;
import org.gitlab.api.models.GitlabMergeRequest;
import org.gitlab.api.models.GitlabNote;
import org.gitlab.api.models.GitlabProject;

public class GitlabMergeRequestWrapper {

    private static final Logger _logger = Logger.getLogger(GitlabMergeRequestWrapper.class.getName());
    private final Integer _id;
    private Integer _iid;
    private final String _author;
    private GitlabProject _sourceProject;
    private String _sourceBranch;
    private String _targetBranch;

    private boolean _shouldRun = false;
    
    private GitlabMergeRequestStatus _mergeRequestStatus;

    transient private GitlabProject _project;
    transient private GitlabMergeRequestBuilder _builder;


    GitlabMergeRequestWrapper(GitlabMergeRequest mergeRequest, GitlabMergeRequestBuilder builder, GitlabProject project) {
        _id = mergeRequest.getId();
        _iid = mergeRequest.getIid();
        _author = mergeRequest.getAuthor().getUsername();
        _sourceBranch = mergeRequest.getSourceBranch();
        try {
            _sourceProject = getSourceProject(mergeRequest, builder.getGitlab().get());
        } catch (IOException e) {
            // Ignore
        }
        _targetBranch = mergeRequest.getTargetBranch();
        _project = project;
        _builder = builder;
        _mergeRequestStatus = new GitlabMergeRequestStatus();
    }

    public void init(GitlabMergeRequestBuilder builder, GitlabProject project) {
        _project = project;
        _builder = builder;
    }

    public void check(GitlabMergeRequest gitlabMergeRequest) {
        if (_iid == null) {
            _iid = gitlabMergeRequest.getIid();
        }

        if (_targetBranch == null) {
            _targetBranch = gitlabMergeRequest.getTargetBranch();
        }

        if (_sourceBranch == null) {
            _sourceBranch = gitlabMergeRequest.getSourceBranch();
        }

        if (_sourceProject == null) {
            try {
                GitlabAPI api = _builder.getGitlab().get();
                _sourceProject = getSourceProject(gitlabMergeRequest, api);
            } catch (IOException e) {
                _logger.log(Level.SEVERE, "Failed to get source project for Merge request " + gitlabMergeRequest.getId() + " :\n" + e.getMessage());
                return;
            }
        }
        
        try {
            GitlabAPI api = _builder.getGitlab().get();
            GitlabNote lastJenkinsNote = getJenkinsNote(gitlabMergeRequest, api);
            GitlabCommit latestCommit = getLatestCommit(gitlabMergeRequest, api);

            if (lastJenkinsNote == null) {
                _shouldRun = true;
            } else {
                _shouldRun = latestCommitIsNotReached(latestCommit);
            }
            if (_shouldRun) {
            	_mergeRequestStatus.setLatestCommitOfMergeRequest(_id.toString(), latestCommit.getId());
            }
        } catch (IOException e) {
            _logger.log(Level.SEVERE, "Failed to fetch commits for Merge Request " + gitlabMergeRequest.getId());
        }

        if (_shouldRun) {
            build();
        }
    }

    private boolean latestCommitIsNotReached(GitlabCommit latestCommit) {
    	String _lastCommit = _mergeRequestStatus.getLatestCommitOfMergeRequest(_id.toString());
    	if (_lastCommit != null && _lastCommit.equals(latestCommit.getId())){
    		return false;
    	}
        return true;
    }

    private GitlabNote getJenkinsNote(GitlabMergeRequest gitlabMergeRequest, GitlabAPI api) throws IOException {
        List<GitlabNote> notes = api.getAllNotes(gitlabMergeRequest);
        GitlabNote lastJenkinsNote = null;

        if (!notes.isEmpty()) {
            Collections.sort(notes, new Comparator<GitlabNote>() {
                public int compare(GitlabNote o1, GitlabNote o2) {
                    return o2.getCreatedAt().compareTo(o1.getCreatedAt());
                }
            });

            for (GitlabNote note : notes) {
                if (note.getAuthor() != null &&
                        note.getAuthor().getUsername().equals(GitlabBuildTrigger.getDesc().getBotUsername())) {
                    lastJenkinsNote = note;
                    break;
                }
            }
        }
        return lastJenkinsNote;
    }

    private GitlabCommit getLatestCommit(GitlabMergeRequest gitlabMergeRequest, GitlabAPI api) throws IOException {
        List<GitlabCommit> commits = api.getCommits(gitlabMergeRequest);
        Collections.sort(commits, new Comparator<GitlabCommit>() {
            public int compare(GitlabCommit o1, GitlabCommit o2) {
                return o2.getCreatedAt().compareTo(o1.getCreatedAt());
            }
        });

        if (commits.isEmpty()) {
            _logger.log(Level.SEVERE, "Merge Request without commits.");
            return null;
        }

        return commits.get(0);
    }

    private GitlabProject getSourceProject(GitlabMergeRequest gitlabMergeRequest, GitlabAPI api) throws IOException {
        return api.getProject(gitlabMergeRequest.getSourceProjectId());
    }

    public Integer getId() {
        return _id;
    }

    public Integer getIid() {
        return _iid;
    }

    public String getAuthor() {
        return _author;
    }

    public String getSourceName() {
        return _sourceProject.getPathWithNamespace();
    }

    public String getSourceRepository() {
        return _sourceProject.getSshUrl();
    }

    public String getSourceBranch() {
        return _sourceBranch;
    }

    public String getTargetBranch() {
        return _targetBranch;
    }

    public GitlabNote createNote(String message) {
        GitlabMergeRequest mergeRequest = new GitlabMergeRequest();
        mergeRequest.setId(_id);
        mergeRequest.setIid(_iid);
        mergeRequest.setProjectId(_project.getId());

        try {
            return _builder.getGitlab().get().createNote(mergeRequest, message);
        } catch (IOException e) {
            _logger.log(Level.SEVERE, "Failed to create note for merge request " + _id, e);
            return null;
        }
    }

    private void build() {
        _shouldRun = false;
        String message = _builder.getBuilds().build(this);

        if (_builder.isEnableBuildTriggeredMessage()) {
            createNote(message);
            _logger.log(Level.INFO, message);
        }
    }

}
