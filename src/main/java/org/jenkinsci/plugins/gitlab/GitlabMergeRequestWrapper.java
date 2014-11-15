package org.jenkinsci.plugins.gitlab;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
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

        if (_mergeRequestStatus == null) {
            _mergeRequestStatus = new GitlabMergeRequestStatus();
        }

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
        if (isAllowedByTargetBranchRegex(_targetBranch)) {
            _logger.log(Level.INFO, "The target regex matches the target branch {" + _targetBranch + "}. Source branch {" + _sourceBranch + "}");
            _shouldRun = true;
        } else {
            _logger.log(Level.INFO, "The target regex did not match the target branch {" + _targetBranch + "}. Not triggering this job. Source branch {" + _sourceBranch + "}");
            return;
        }
        try {
            GitlabAPI api = _builder.getGitlab().get();
            GitlabNote lastJenkinsNote = getJenkinsNote(gitlabMergeRequest, api);
            GitlabCommit latestCommit = getLatestCommit(gitlabMergeRequest, api);

            if (lastJenkinsNote == null) {
                _logger.info("Latest note from Jenkins is null");
                _shouldRun = true;
            } else if (latestCommit == null) {
                _logger.log(Level.SEVERE, "Failed to determine the lastest commit for merge request {" + gitlabMergeRequest.getId() + "}. This might be caused by a stalled MR in gitlab.");
                return;
            } else {
                _logger.info("Latest note from Jenkins: " + lastJenkinsNote.getId().toString());
                _shouldRun = latestCommitIsNotReached(latestCommit);
                _logger.info("Latest commit: " + latestCommit.getId());
            }
            if (_shouldRun) {
                _logger.info("Build is supposed to run");
                _mergeRequestStatus.setLatestCommitOfMergeRequest(_id.toString(), latestCommit.getId());
            }
        } catch (IOException e) {
            _logger.log(Level.SEVERE, "Failed to fetch commits for Merge Request " + gitlabMergeRequest.getId());
        }

        if (_shouldRun) {
            build();
        }
    }

    /**
     * Check whether the branchName can be matched using the target branch 
     * regex. Empty regex patterns will cause this method to return true. 
     * 
     * @param branchName
     * @return true when the name can be matched or when the regex is empty.
     *         Otherwise false.
     */
    public boolean isAllowedByTargetBranchRegex(String branchName) {
        String regex =  _builder.getTrigger().getTargetBranchRegex();
        // Allow when no pattern has been specified. (default behavior)
        if (StringUtils.isEmpty(regex)) {
           return true;
        }
        Pattern pattern = Pattern.compile(regex);
        return pattern.matcher(branchName).matches();
    }

    private boolean latestCommitIsNotReached(GitlabCommit latestCommit) {
        String _lastCommit = _mergeRequestStatus.getLatestCommitOfMergeRequest(_id.toString());
        if (_lastCommit != null) {
            _logger.info("Latest commit Jenkins remembers is " + _lastCommit);
            return ! _lastCommit.equals(latestCommit.getId());
        } else {
            _logger.info("Jenkins does not remember any commit of this MR");
        }
        return true;
    }

    private GitlabNote getJenkinsNote(GitlabMergeRequest gitlabMergeRequest, GitlabAPI api) throws IOException {
        List<GitlabNote> notes = api.getAllNotes(gitlabMergeRequest);
        _logger.info("Notes found: " + Integer.toString(notes.size()));

        GitlabNote lastJenkinsNote = null;

        if (!notes.isEmpty()) {
            Collections.sort(notes, new Comparator<GitlabNote>() {
                public int compare(GitlabNote o1, GitlabNote o2) {
                    return o2.getCreatedAt().compareTo(o1.getCreatedAt());
                }
            });

            String botUsernameNormalized = this.normalizeUsername(GitlabBuildTrigger.getDesc().getBotUsername());

            for (GitlabNote note : notes) {
                if (note.getAuthor() != null) {
                    String noteAuthorNormalized = this.normalizeUsername(note.getAuthor().getUsername());
                    _logger.finest(
                        "Traversing notes. Author: " + note.getAuthor().getUsername() + "; " +
                         "normalized: " + noteAuthorNormalized
                    );

                    if (noteAuthorNormalized.equals(botUsernameNormalized)) {
                        lastJenkinsNote = note;
                        break;
                    }
                }
            }
        }
        return lastJenkinsNote;
    }

    private String normalizeUsername(String username) {
        return username.toLowerCase();
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
        if (_builder.getTrigger().getUseHttpUrl()) {
            return _sourceProject.getHttpUrl();
        } else {
            return _sourceProject.getSshUrl();
        }
    }

    public String getSourceBranch() {
        return _sourceBranch;
    }

    public String getTargetBranch() {
        return _targetBranch;
    }

 
    public GitlabNote createNote(String message, boolean shouldClose) {
        GitlabMergeRequest mergeRequest = new GitlabMergeRequest();
        mergeRequest.setId(_id);
        mergeRequest.setIid(_iid);
        mergeRequest.setProjectId(_project.getId());

        try {
            if(shouldClose){
                String tailUrl = _project.URL + "/" + _project.getId() + "/merge_request/" + _id + "?state_event=close";
                _builder.getGitlab().get().retrieve().method("PUT").to(tailUrl, Void.class);
            }
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
            createNote(message, false);
            _logger.log(Level.INFO, message);
        }
    }

}
