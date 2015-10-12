package org.jenkinsci.plugins.gitlab;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
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
    private String _description;
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
        _description = mergeRequest.getDescription();
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

        if (_description == null) {
            _description = gitlabMergeRequest.getDescription();
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
            GitlabNote lastNote = getLastNote(gitlabMergeRequest, api);
            GitlabCommit latestCommit = getLatestCommit(gitlabMergeRequest, api);
            String assigneeFilter = _builder.getTrigger().getAssigneeFilter();
            String assignee = getAssigneeUsername(gitlabMergeRequest);;
            String triggerComment = _builder.getTrigger().getTriggerComment();
            if (lastJenkinsNote == null) {
                _logger.info("Latest note from Jenkins is null");
                _shouldRun = latestCommitIsNotReached(latestCommit);
            } else if (latestCommit == null) {
                _logger.log(Level.SEVERE, "Failed to determine the lastest commit for merge request {" + gitlabMergeRequest.getId() + "}. This might be caused by a stalled MR in gitlab.");
                return;
            } else {
                _logger.info("Latest note from Jenkins: " + lastJenkinsNote.getBody());
                _shouldRun = latestCommitIsNotReached(latestCommit);
                _logger.info("Latest commit: " + latestCommit.getId());
                
                if (lastNote.getBody().equals(triggerComment)) {
                    _shouldRun = true;
                }
            }
            if (_shouldRun) {
                if (assigneeFilterMatch(assigneeFilter, assignee)) {
                    _logger.info("Build is supposed to run");
                    _mergeRequestStatus.setLatestCommitOfMergeRequest(_id.toString(), latestCommit.getId());
                } else {
                    _shouldRun = false;
                }
            }
            if (_shouldRun) {
            	Map<String, String> customParameters = getSpecifiedCustomParameters(gitlabMergeRequest, api);
            	build(customParameters);
            }
        } catch (IOException e) {
            _logger.log(Level.SEVERE, "Failed to fetch commits for Merge Request " + gitlabMergeRequest.getId());
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

    private String getAssigneeUsername(GitlabMergeRequest gitlabMergeRequest) {
        String assigneeUsername = "";
        if (gitlabMergeRequest.getAssignee() != null) {
            assigneeUsername = gitlabMergeRequest.getAssignee().getUsername();
        } 
        return assigneeUsername;
    }
    
    private boolean assigneeFilterMatch(String assigneeFilter, String assignee) {
        boolean shouldRun = true;
        if (assigneeFilter.equals("")) {
            shouldRun = true;
        } else {
            if(assignee.equals(assigneeFilter)) {
                shouldRun = true;
            } else {
                shouldRun = false;
                _logger.info("Assignee: " + assignee + " does not match " + assigneeFilter);
            }
        }
        return shouldRun;
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
    
    private GitlabNote getLastNote(GitlabMergeRequest gitlabMergeRequest, GitlabAPI api) throws IOException {
        List<GitlabNote> notes = getNotes(gitlabMergeRequest, api);
        
        GitlabNote lastNote = null;

        if (!notes.isEmpty()) {
            lastNote = notes.get(notes.size()-1);
            _logger.info("Last note found: " + lastNote.getBody());
        }
        return lastNote;
    }
    
    private List<GitlabNote> getNotes(GitlabMergeRequest gitlabMergeRequest, GitlabAPI api) throws IOException {
        List<GitlabNote> notes = api.getAllNotes(gitlabMergeRequest);
        
        Collections.sort(notes, new Comparator<GitlabNote>() {
            public int compare(GitlabNote o1, GitlabNote o2) {
                return o1.getCreatedAt().compareTo(o2.getCreatedAt());
            }
        });
        return notes;
    }

    private Map<String, String> getSpecifiedCustomParameters(GitlabMergeRequest gitlabMergeRequest, GitlabAPI api) throws IOException {
        String botUsername = GitlabBuildTrigger.getDesc().getBotUsername();
        // Mention the botUserName in the text using @[botUserName] to indicate a command to the bot. If that is followed by a semicolon and one of the two commands:
        //  USE-PARAMETER for specifying a parameter using the format Key=Value
        // or REMOVE-PARAMETER for removing a parameter with the given Key
        Pattern searchPattern = Pattern.compile("@"+botUsername+"\\s*:\\s*(USE|REMOVE)-PARAMETER\\s*:\\s*(\\w+)\\s*(?:=\\s*(.*))?", Pattern.CASE_INSENSITIVE);
        
        Map<String, String> customParams = new HashMap<>();
        for (GitlabNote note: getNotes(gitlabMergeRequest, api)) {
            Matcher m = searchPattern.matcher(note.getBody());
            // the command to the @botUserName can be given anywhere in the text
            if (m.find()) {
                if(m.group(1).equalsIgnoreCase("USE")) {
                    customParams.put(m.group(2), m.group(3));
                } else {
                    customParams.remove(m.group(2));
                }
            }
        }
        return customParams;
    }
    
    private GitlabNote getJenkinsNote(GitlabMergeRequest gitlabMergeRequest, GitlabAPI api) throws IOException {
        List<GitlabNote> notes = getNotes(gitlabMergeRequest, api);
        _logger.info("Notes found: " + Integer.toString(notes.size()));

        GitlabNote lastJenkinsNote = null;

        if (!notes.isEmpty()) {
            String botUsernameNormalized = this.normalizeUsername(GitlabBuildTrigger.getDesc().getBotUsername());
            Collections.reverse(notes);
            
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

    public String getDescription() {
        return _description;
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

    public GitlabNote createNote(String message, boolean shouldClose, boolean shouldMerge) {
        GitlabMergeRequest mergeRequest = new GitlabMergeRequest();
        mergeRequest.setId(_id);
        mergeRequest.setIid(_iid);
        mergeRequest.setProjectId(_project.getId());

        try {
            if(shouldClose || shouldMerge){
                String tailUrl = "";
                if(shouldClose){
                    tailUrl = _project.URL + "/" + _project.getId() + "/merge_request/" + _id + "?state_event=close";
                }
                if(shouldMerge){
                    tailUrl = _project.URL + "/" + _project.getId() + "/merge_request/" + _id + "/merge";
                }
                _builder.getGitlab().get().retrieve().method("PUT").to(tailUrl, Void.class);
            }
        } catch (IOException e) {
            _logger.log(Level.SEVERE, "Failed to automatically merge/close the merge request " + _id, e);
        }

        try{
            return _builder.getGitlab().get().createNote(mergeRequest, message);
        } catch (IOException e) {
            _logger.log(Level.SEVERE, "Failed to create note for merge request " + _id, e);
            return null;
        }

    }

    private void build(Map<String, String> customParameters) {
        _shouldRun = false;
        String message = _builder.getBuilds().build(this, customParameters);

        if (_builder.isEnableBuildTriggeredMessage()) {
            createNote(message, false, false);
            _logger.log(Level.INFO, message);
        }
    }

}
