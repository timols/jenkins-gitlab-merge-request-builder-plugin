package org.jenkinsci.plugins.gitlab;

import org.apache.commons.lang.StringUtils;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabCommit;
import org.gitlab.api.models.GitlabCommitStatus;
import org.gitlab.api.models.GitlabMergeRequest;
import org.gitlab.api.models.GitlabNote;
import org.gitlab.api.models.GitlabProject;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitlabMergeRequestWrapper {

    private static final Logger LOGGER = Logger.getLogger(GitlabMergeRequestWrapper.class.getName());
    private final Integer id;
    private Integer iid;
    private final String author;
    private String description;
    private GitlabProject sourceProject;
    private String sourceBranch;
    private String targetBranch;

    private boolean shouldRun = false;

    private GitlabMergeRequestStatus mergeRequestStatus;

    transient private GitlabProject project;
    transient private GitlabMergeRequestBuilder builder;


    GitlabMergeRequestWrapper(GitlabMergeRequest mergeRequest, GitlabMergeRequestBuilder builder, GitlabProject project) {
        this.id = mergeRequest.getId();
        this.iid = mergeRequest.getIid();
        this.author = mergeRequest.getAuthor().getUsername();
        this.description = mergeRequest.getDescription();
        this.sourceBranch = mergeRequest.getSourceBranch();

        try {
            this.sourceProject = getSourceProject(mergeRequest, builder.getGitlab().get());
        } catch (IOException e) {
            // Ignore
        }
        this.targetBranch = mergeRequest.getTargetBranch();
        this.project = project;
        this.builder = builder;
        this.mergeRequestStatus = new GitlabMergeRequestStatus();
    }

    public void init(GitlabMergeRequestBuilder builder, GitlabProject project) {
        this.project = project;
        this.builder = builder;
    }

    public void check(GitlabMergeRequest gitlabMergeRequest) {

        if (mergeRequestStatus == null) {
            mergeRequestStatus = new GitlabMergeRequestStatus();
        }

        if (iid == null) {
            iid = gitlabMergeRequest.getIid();
        }

        if (targetBranch == null) {
            targetBranch = gitlabMergeRequest.getTargetBranch();
        }

        if (sourceBranch == null) {
            sourceBranch = gitlabMergeRequest.getSourceBranch();
        }

        if (description == null) {
            description = gitlabMergeRequest.getDescription();
        }

        if (sourceProject == null) {
            try {
                GitlabAPI api = builder.getGitlab().get();
                sourceProject = getSourceProject(gitlabMergeRequest, api);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to get source project for Merge request " + gitlabMergeRequest.getId() + " :\n" + e.getMessage());
                return;
            }
        }
        if (isAllowedByTargetBranchRegex(targetBranch)) {
            LOGGER.log(Level.INFO, "The target regex matches the target branch {" + targetBranch + "}. Source branch {" + sourceBranch + "}");
            shouldRun = true;
        } else {
            LOGGER.log(Level.INFO, "The target regex did not match the target branch {" + targetBranch + "}. Not triggering this job. Source branch {" + sourceBranch + "}");
            return;
        }
        try {
            GitlabAPI api = builder.getGitlab().get();
            GitlabNote lastJenkinsNote = getJenkinsNote(gitlabMergeRequest, api);
            GitlabNote lastNote = getLastNote(gitlabMergeRequest, api);
            GitlabCommit latestCommit = getLatestCommit(gitlabMergeRequest, api);
            String assigneeFilter = builder.getTrigger().getAssigneeFilter();
            String assignee = getAssigneeUsername(gitlabMergeRequest);
            String triggerComment = builder.getTrigger().getTriggerComment();
            if (lastJenkinsNote == null) {
                LOGGER.info("Latest note from Jenkins is null");
                shouldRun = latestCommitIsNotReached(latestCommit);
            } else if (latestCommit == null) {
                LOGGER.log(Level.SEVERE, "Failed to determine the lastest commit for merge request {" + gitlabMergeRequest.getId() + "}. This might be caused by a stalled MR in gitlab.");
                return;
            } else {
                LOGGER.info("Latest note from Jenkins: " + lastJenkinsNote.getBody());
                shouldRun = latestCommitIsNotReached(latestCommit);
                LOGGER.info("Latest commit: " + latestCommit.getId());

                if (lastNote.getBody().equals(triggerComment)) {
                    shouldRun = true;
                }
            }
            if (shouldRun) {
                if (assigneeFilterMatch(assigneeFilter, assignee)) {
                    LOGGER.info("Build is supposed to run");
                    mergeRequestStatus.setLatestCommitOfMergeRequest(id.toString(), latestCommit.getId());
                } else {
                    shouldRun = false;
                }
            }
            if (shouldRun) {
                Map<String, String> customParameters = getSpecifiedCustomParameters(gitlabMergeRequest, api);
                build(customParameters);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch commits for Merge Request " + gitlabMergeRequest.getId());
        }
    }

    /**
     * Check whether the branchName can be matched using the target branch
     * regex. Empty regex patterns will cause this method to return true.
     *
     * @param branchName
     * @return true when the name can be matched or when the regex is empty.
     * Otherwise false.
     */
    public boolean isAllowedByTargetBranchRegex(String branchName) {
        String regex = builder.getTrigger().getTargetBranchRegex();
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
            if (assignee.equals(assigneeFilter)) {
                shouldRun = true;
            } else {
                shouldRun = false;
                LOGGER.info("Assignee: " + assignee + " does not match " + assigneeFilter);
            }
        }
        return shouldRun;
    }

    private boolean latestCommitIsNotReached(GitlabCommit latestCommit) {
        String lastCommit = mergeRequestStatus.getLatestCommitOfMergeRequest(id.toString());
        if (lastCommit != null) {
            LOGGER.info("Latest commit Jenkins remembers is " + lastCommit);
            return !lastCommit.equals(latestCommit.getId());
        } else {
            LOGGER.info("Jenkins does not remember any commit of this MR");
        }
        return true;
    }

    private GitlabNote getLastNote(GitlabMergeRequest gitlabMergeRequest, GitlabAPI api) throws IOException {
        List<GitlabNote> notes = getNotes(gitlabMergeRequest, api);

        GitlabNote lastNote = null;

        if (!notes.isEmpty()) {
            lastNote = notes.get(notes.size() - 1);
            LOGGER.info("Last note found: " + lastNote.getBody());
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
        Pattern searchPattern = Pattern.compile("@" + botUsername + "\\s*:\\s*(USE|REMOVE)-PARAMETER\\s*:\\s*(\\w+)\\s*(?:=\\s*(.*))?", Pattern.CASE_INSENSITIVE);

        Map<String, String> customParams = new HashMap<>();
        for (GitlabNote note : getNotes(gitlabMergeRequest, api)) {
            Matcher m = searchPattern.matcher(note.getBody());
            // the command to the @botUserName can be given anywhere in the text
            if (m.find()) {
                if (m.group(1).equalsIgnoreCase("USE")) {
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
        LOGGER.info("Notes found: " + Integer.toString(notes.size()));

        GitlabNote lastJenkinsNote = null;

        if (!notes.isEmpty()) {
            String botUsernameNormalized = this.normalizeUsername(GitlabBuildTrigger.getDesc().getBotUsername());
            Collections.reverse(notes);

            for (GitlabNote note : notes) {
                if (note.getAuthor() != null) {
                    String noteAuthorNormalized = this.normalizeUsername(note.getAuthor().getUsername());
                    LOGGER.finest(
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
            LOGGER.log(Level.SEVERE, "Merge Request without commits.");
            return null;
        }

        return commits.get(0);
    }

    private GitlabProject getSourceProject(GitlabMergeRequest gitlabMergeRequest, GitlabAPI api) throws IOException {
        return api.getProject(gitlabMergeRequest.getSourceProjectId());
    }

    public Integer getId() {
        return id;
    }

    public Integer getIid() {
        return iid;
    }

    public String getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    public String getSourceName() {
        return sourceProject.getPathWithNamespace();
    }

    public String getSourceRepository() {
        if (builder.getTrigger().getUseHttpUrl()) {
            return sourceProject.getHttpUrl();
        } else {
            return sourceProject.getSshUrl();
        }
    }

    public String getSourceBranch() {
        return sourceBranch;
    }

    public String getTargetBranch() {
        return targetBranch;
    }

    public GitlabNote createNote(String message, boolean shouldClose, boolean shouldMerge) {
        GitlabMergeRequest mergeRequest = new GitlabMergeRequest();
        mergeRequest.setId(id);
        mergeRequest.setIid(iid);
        mergeRequest.setProjectId(project.getId());

        try {
            if (shouldClose || shouldMerge) {
                String tailUrl = "";
                if (shouldClose) {
                    tailUrl = GitlabProject.URL + "/" + project.getId() + "/mergerequest/" + id + "?stateevent=close";
                }
                if (shouldMerge) {
                    tailUrl = GitlabProject.URL + "/" + project.getId() + "/mergerequest/" + id + "/merge";
                }
                builder.getGitlab().get().retrieve().method("PUT").to(tailUrl, Void.class);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to automatically merge/close the merge request " + id, e);
        }

        try {
            return builder.getGitlab().get().createNote(mergeRequest, message);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to create note for merge request " + id, e);
            return null;
        }

    }

    public GitlabCommitStatus createCommitStatus(String status, String targetUrl) {
        try {
            GitlabAPI api = builder.getGitlab().get();
            GitlabMergeRequest mergeRequest = api.getMergeRequest(project, id);
            GitlabCommit latestCommit = getLatestCommit(mergeRequest, api);

            if (latestCommit != null) {
                return api.createCommitStatus(project, latestCommit.getId(), status, mergeRequest.getSourceBranch(), "Jenkins", targetUrl, null);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to set commit status for merge request " + id, e);
        }

        return null;
    }

    private void build(Map<String, String> customParameters) {
        shouldRun = false;
        String message = builder.getBuilds().build(this, customParameters);

        if (builder.isEnableBuildTriggeredMessage()) {
            createNote(message, false, false);
            LOGGER.log(Level.INFO, message);
        }
    }

}
