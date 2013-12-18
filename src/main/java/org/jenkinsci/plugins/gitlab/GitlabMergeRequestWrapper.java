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
    private String _source;
    private String _target;

    private boolean _shouldRun = false;

    transient private GitlabProject _project;
    transient private GitlabMergeRequestBuilder _builder;


    GitlabMergeRequestWrapper(GitlabMergeRequest mergeRequest, GitlabMergeRequestBuilder builder, GitlabProject project) {
        _id = mergeRequest.getId();
        _iid = mergeRequest.getIid();
        _author = mergeRequest.getAuthor().getUsername();
        _source = mergeRequest.getSourceBranch();
        _target = mergeRequest.getTargetBranch();
        _project = project;
        _builder = builder;
    }

    public void init(GitlabMergeRequestBuilder builder, GitlabProject project) {
        _project = project;
        _builder = builder;
    }

    public void check(GitlabMergeRequest gitlabMergeRequest) {
        if (_iid == null) {
            _iid = gitlabMergeRequest.getIid();
        }

        if (_target == null) {
            _target = gitlabMergeRequest.getTargetBranch();
        }

        if (_source == null) {
            _source = gitlabMergeRequest.getSourceBranch();
        }

        try {
            GitlabAPI api = _builder.getGitlab().get();
            GitlabNote lastJenkinsNote = getJenkinsNote(gitlabMergeRequest, api);

            if (lastJenkinsNote == null) {
                _shouldRun = true;
            } else {
                GitlabCommit latestCommit = getLatestCommit(gitlabMergeRequest, api);

                if (latestCommit != null) {
                    _shouldRun = latestCommit.getCreatedAt().after(lastJenkinsNote.getCreatedAt());
                }
            }
        } catch (IOException e) {
            _logger.log(Level.SEVERE, "Failed to fetch commits for Merge Request " + gitlabMergeRequest.getId());
        }

        if (_shouldRun) {
            build();
        }
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

    public Integer getId() {
        return _id;
    }

    public Integer getIid() {
        return _iid;
    }

    public String getAuthor() {
        return _author;
    }

    public String getSource() {
        return _source;
    }

    public String getTarget() {
        return _target;
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
