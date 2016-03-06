package org.jenkinsci.plugins.gitlab;

import org.apache.commons.lang.StringUtils;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.*;

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
        } catch (IOException ex) {
        	LOGGER.throwing("GitlabMergeRequestWrapper", "constructor", ex);
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

    public void setLatestCommitOfMergeRequest(String _requestId,
                                         String latestCommit) {
        mergeRequestStatus.setLatestCommitOfMergeRequest(_requestId, latestCommit);
    }

    public void check(GitlabMergeRequest gitlabMergeRequest) {

        if (mergeRequestStatus == null) {
            mergeRequestStatus = new GitlabMergeRequestStatus();
        }

        if (iid == null) {
            iid = gitlabMergeRequest.getIid();
        }

        if (targetBranch == null || targetBranch.trim().isEmpty()) {
            targetBranch = gitlabMergeRequest.getTargetBranch();
        }

        if (sourceBranch == null || sourceBranch.trim().isEmpty()) {
            sourceBranch = gitlabMergeRequest.getSourceBranch();
        }

        if (description == null || description.trim().isEmpty()) {
            description = gitlabMergeRequest.getDescription();
            
            if (description == null) { description = ""; }
        }

        if (sourceProject == null || sourceProject.getId() == null || sourceProject.getName() == null) {
            try {
                GitlabAPI api = builder.getGitlab().get();
                sourceProject = getSourceProject(gitlabMergeRequest, api);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to get source project for Merge request " + gitlabMergeRequest.getId() + " :\n" + e.getMessage());
                return;
            }
        }
        
        try {
            GitlabAPI api = builder.getGitlab().get();
            GitlabCommit latestCommit = getLatestCommit(gitlabMergeRequest, api);

            if (latestCommit == null) { // the source branch has been removed
                return;
            }
            
            Map<String, String> customParameters = getSpecifiedCustomParameters(gitlabMergeRequest, api);
            build(customParameters, latestCommit.getId(), gitlabMergeRequest);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch commits for Merge Request " + gitlabMergeRequest.getId());
        }
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
                    tailUrl = GitlabProject.URL + "/" + project.getId() + "/merge_request/" + id + "?state_event=close";
                }
                if (shouldMerge) {
                    tailUrl = GitlabProject.URL + "/" + project.getId() + "/merge_request/" + id + "/merge";
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

    public GitlabCommitStatus changeCommitStatus(String commitHash, String commitStatus, String targetUrl) {

        try {
            GitlabAPI api = builder.getGitlab().get();
            GitlabMergeRequest mergeRequest = api.getMergeRequest(project, id);

            return builder.getGitlab().changeCommitStatus(project.getId(), mergeRequest.getSourceBranch(), commitHash, commitStatus, targetUrl);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to change status for merge request commit " + commitHash, e);
        }

        return null;
    }

    private void build(Map<String, String> customParameters, String commitHash, GitlabMergeRequest mergeRequest) {
        shouldRun = false;
        
        GitlabCause cause = new GitlabCause(
        		this.getId(),
        		this.getIid(),
        		this.getSourceName(),
        		this.getSourceRepository(),
        		this.getSourceBranch(),
        		this.getTargetBranch(),
                customParameters,
                this.getDescription(),
                this.sourceProject.getId(),
                project.getId(),
                commitHash);
        
		try {
			String message = builder.getBuilds().build(cause, customParameters, project, mergeRequest);
			
			if (builder.isEnableBuildTriggeredMessage()) {
	            createNote(message, false, false);
	            LOGGER.log(Level.INFO, message);
	        }
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

}
