package com.jenkinsci.plugins.gitlab;

import hudson.util.Secret;
import mockit.Injectable;
import mockit.Mock;
import mockit.MockUp;
import mockit.NonStrictExpectations;
import mockit.Tested;
import mockit.integration.junit4.JMockit;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabCommit;
import org.gitlab.api.models.GitlabMergeRequest;
import org.gitlab.api.models.GitlabNote;
import org.gitlab.api.models.GitlabProject;
import org.gitlab.api.models.GitlabUser;
import org.jenkinsci.plugins.gitlab.GitlabBuildTrigger;
import org.jenkinsci.plugins.gitlab.GitlabMergeRequestBuilder;
import org.jenkinsci.plugins.gitlab.GitlabMergeRequestStatus;
import org.jenkinsci.plugins.gitlab.GitlabMergeRequestWrapper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Map;

import static mockit.Deencapsulation.getField;

@RunWith(JMockit.class)
public class GitlabMergeRequestWrapper_check_Test {
    @Injectable GitlabAPI api;
    @Injectable GitlabBuildTrigger trigger;
    @Injectable GitlabMergeRequest mergeRequest;
    @Injectable GitlabMergeRequestBuilder builder;
    @Injectable GitlabProject project;
    @Tested GitlabMergeRequestWrapper subject;
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-DD");
    GitlabCommit commit = new GitlabCommit();
    GitlabNote jenkinsNote = new GitlabNote();
    GitlabNote triggerNote = new GitlabNote();
    GitlabUser botUser = new GitlabUser();

    // Mock dependencies used during static initialization.
    @BeforeClass
    public static void beforeClass() throws Exception {
        new MockUp<GitlabBuildTrigger.GitlabBuildTriggerDescriptor>() {
            @Mock void load() {}
        };

        new MockUp<Secret>() {
            @Mock Secret fromString(String data) { return null; }
        };
    }

    @Before
    public void before() throws Exception {
        new MockUp<GitlabBuildTrigger.GitlabBuildTriggerDescriptor>() {
            @Mock String getBotUsername() { return "test-bot-username"; }
        };

        new NonStrictExpectations() {{
            api.getCommits(mergeRequest); result = Arrays.asList(commit);
            trigger.getAssigneeFilter(); result = "";
            trigger.getTriggerComment(); result = "test-trigger-comment";
        }};

        botUser.setUsername("test-bot-username");
        commit.setId("test-commit-id");
        jenkinsNote.setAuthor(botUser);
        jenkinsNote.setBody("test-jenkins-note");
        jenkinsNote.setCreatedAt(dateFormat.parse("2015-01-01"));
        triggerNote.setBody("test-trigger-comment");
        triggerNote.setCreatedAt(dateFormat.parse("2015-01-02"));
    }

    @Test
    public void builds() throws Exception {
    	
    	new MockUp<GitlabMergeRequestWrapper>() {
            @Mock(invocations = 1) void build(Map m, String s, GitlabMergeRequest mr) {}
        };
    	
    	setAReachableLatestCommit();
    	
        subject.check(mergeRequest);
    }

    private void setAReachableLatestCommit() {
        ((GitlabMergeRequestStatus) getField(subject, "mergeRequestStatus")).setLatestCommitOfMergeRequest(
                mergeRequest.getId().toString(),
                commit.getId());
    }
}
