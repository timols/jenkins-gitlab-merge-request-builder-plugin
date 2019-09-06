package org.jenkinsci.plugins.gitlab;

import hudson.model.queue.QueueTaskFuture;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.Comparator;
import java.util.Collections;

import org.apache.commons.lang.StringUtils;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabCommitStatus;
import org.gitlab.api.models.GitlabMergeRequest;
import org.gitlab.api.models.GitlabNote;
import org.gitlab.api.models.GitlabProject;

public class GitlabBuilds {

    private static final Logger LOGGER = Logger.getLogger(GitlabBuilds.class.getName());

    private GitlabBuildTrigger trigger;
    private GitlabRepository repository;

    public GitlabBuilds(GitlabBuildTrigger trigger, GitlabRepository repository) {
        this.trigger = trigger;
        this.repository = repository;
    }

    public String build(GitlabCause cause, Map<String, String> customParameters, GitlabProject project, GitlabMergeRequest mergeRequest) throws IOException {

        boolean shouldRun = true;
        GitlabAPI api = trigger.getBuilder().getGitlab().get();
        String triggerComment = trigger.getTriggerComment();
        GitlabNote lastNote = getLastNote(mergeRequest, api);

        if (isAllowedBySourceBranchRegex(cause.getSourceBranch())) {
            LOGGER.log(Level.INFO, "The source regex matches the source branch {" + cause.getSourceBranch() + "}. Target branch {" + cause.getTargetBranch() + "}");
            shouldRun = true;
        } else {
            LOGGER.log(Level.INFO, "The source regex did not match the source branch {" + cause.getSourceBranch() + "}. Not triggering this job. Target branch {" + cause.getTargetBranch() + "}");
            shouldRun = false;
        }

        if (shouldRun && isAllowedByTargetBranchRegex(cause.getTargetBranch())) {
            LOGGER.log(Level.INFO, "The target regex matches the target branch {" + cause.getTargetBranch() + "}. Source branch {" + cause.getSourceBranch() + "}");
            shouldRun = true;
        } else {
            LOGGER.log(Level.INFO, "The target regex did not match the target branch {" + cause.getTargetBranch() + "}. Not triggering this job. Source branch {" + cause.getSourceBranch() + "}");
            shouldRun = false;
        }

        LOGGER.log(Level.INFO, "The merge request state: " + mergeRequest.getState() + ", " + cause.getMergeRequestState());
        // state is close
        if (mergeRequest.isClosed()) {
            LOGGER.log(Level.INFO, "The merge request " + cause.getTitle() + " has been closed.");
            shouldRun = false;
        }

        if (hasCommitStatus(project, cause.getLastCommitId(), api)) {
            LOGGER.log(Level.WARNING, "The merge request " + cause.getTitle() + "  has running/pending pipelines.");
//            shouldRun = false;
        }

        if (lastNote != null && lastNote.getBody().equals(triggerComment)) {
            LOGGER.log(Level.INFO, "Trigger comment found the merge request " + cause.getTitle());
            shouldRun = true;
        }

        if (shouldRun) {
            String assigneeFilter = trigger.getAssigneeFilter();

            if (!"".equals(assigneeFilter)) {
                String assigneeName = mergeRequest.getAssignee() != null ? mergeRequest.getAssignee().getUsername() : null;
                shouldRun = filterMatch(assigneeFilter, assigneeName, "Assignee");
            }
        }

        if (shouldRun) {
            String tagFilter = trigger.getTagFilter();

            if (!"".equals(tagFilter)) {
                shouldRun = filterMatch(tagFilter, mergeRequest.getLabels(), "Labels");
            }
        }

        if (shouldRun) {
            if (isWorkInProgress(mergeRequest.getTitle())) {
                shouldRun = false;
            }
        }

        if (shouldRun) {
            LOGGER.info("Build is supposed to run");

            QueueTaskFuture<?> build = trigger.startJob(cause);
            if (build == null) {
                LOGGER.log(Level.SEVERE, "Job failed to start.");
            }

            return GitlabBuilds.withCustomParameters(new StringBuilder("Build triggered."), customParameters).toString();
        } else {
            LOGGER.info("Build is not supposed to run");
            return "";
        }
    }

    public boolean isWorkInProgress(String title) {
        return null != title && (title.startsWith("[WIP]") || title.startsWith("WIP:"));
    }

    /**
     * Check whether the branchName can be matched using the target branch
     * regex. Empty regex patterns will cause this method to return true.
     *
     * @param branchName String
     * @return true when the name can be matched or when the regex is empty.
     * Otherwise false.
     */
    public boolean isAllowedByTargetBranchRegex(String branchName) {
        String regex = trigger.getTargetBranchRegex();
        // Allow when no pattern has been specified. (default behavior)
        if (StringUtils.isEmpty(regex)) {
            return true;
        }
        Pattern pattern = Pattern.compile(regex);
        return pattern.matcher(branchName).matches();
    }

    public boolean isAllowedBySourceBranchRegex(String branchName) {
        String regex = trigger.getSourceBranchRegex();
        // Allow when no pattern has been specified. (default behavior)
        if (StringUtils.isEmpty(regex)) {
            return true;
        }
        Pattern pattern = Pattern.compile(regex);
        return pattern.matcher(branchName).matches();
    }

    /**
     * synchronized so that there can't be a race condition here.
     *
     * @param project GitlabProject
     * @param commitHash String
     * @param api GitlabAPI
     * @return
     * @throws IOException
     */
    private synchronized boolean hasCommitStatus(GitlabProject project, String commitHash, GitlabAPI api) throws IOException {
        try {
            boolean hasPendingOrRunningStatus = false;
            List<GitlabCommitStatus> statuses = api.getCommitStatuses(project, commitHash);
            // pending, running,
            for (GitlabCommitStatus status : statuses) {
                LOGGER.fine("Status of " + commitHash + " -> " + status.getStatus());
                if (status.getStatus().equals("pending") || status.getStatus().equals("running")) {
                    hasPendingOrRunningStatus = true;
                }
            }

            // Return true if there are some statuses
//            return !statuses.isEmpty();
            return hasPendingOrRunningStatus;

        } catch (FileNotFoundException ex) {
            // Can ignore this one because it just means that there is no status for a commit
        }


        return false;
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

    private boolean filterMatch(String filter, String target, String type) {
        boolean shouldRun = true;
        if ("".equals(filter)) {
            shouldRun = true;
        } else {
            if (target == null)
                return false;

            if (target.equals(filter)) {
                shouldRun = true;
            } else {
                shouldRun = false;
                LOGGER.info(type + ": " + target + " does not match " + filter);
            }
        }
        return shouldRun;
    }

    private boolean filterMatch(String filter, String target[], String type) {
        boolean shouldRun = false;
        if ("".equals(filter)) {
            shouldRun = true;
        } else {
            if (target == null)
                return false;

            for (String test : target) {
                if (test.equals(filter)) {
                    shouldRun = true;
                }
            }

            if (!shouldRun) {
                LOGGER.info(type + ": " + Arrays.toString(target) + " does not contain " + filter);
            }
        }
        return shouldRun;
    }

    public static StringBuilder withCustomParameters(StringBuilder sb, Map<String, String> customParameters) {
        if (customParameters.isEmpty()) {
            return sb;
        }

        sb.append("\n\nUsing custom parameters:");
        for (Map.Entry<String, String> entry : customParameters.entrySet()) {
            sb.append("\n* `");
            sb.append(entry.getKey());
            sb.append("`=`");
            sb.append(entry.getValue());
            sb.append("`");
        }
        sb.append("\n\n");
        return sb;
    }
}
