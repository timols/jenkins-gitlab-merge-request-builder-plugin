package com.jenkinsci.plugins.gitlab;

import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.util.Secret;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import jenkins.model.Jenkins;
import mockit.*;
import mockit.integration.junit4.JMockit;

import static mockit.Deencapsulation.invoke;

import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabCommitStatus;
import org.gitlab.api.models.GitlabMergeRequest;
import org.gitlab.api.models.GitlabProject;
import org.gitlab.api.models.GitlabUser;
import org.jenkinsci.plugins.gitlab.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMockit.class)
public class GitlabBuilds_build_Test {
    @Injectable
    GitlabAPI api;
    @Injectable
    GitlabProject project;
    @Injectable
    GitlabUser user;

    //@Injectable

    GitlabMergeRequest mergeRequest = new GitlabMergeRequest();

    @Injectable
    GitlabBuildTrigger trigger;
    @Injectable
    GitlabRepository repository;

    @Tested
    GitlabBuilds subject;

    GitlabCause cause = new GitlabCause(
            1,
            2,
            "sourceName",
            "sourceRepo",
            "sourceBranch",
            "targetBranch",
            new HashMap<String, String>(),
            "title",
            "description",
            3,
            4,
            "commitHash"
    );

    GitlabCommitStatus status = new GitlabCommitStatus();
    List<GitlabCommitStatus> statuses = Arrays.asList(status);

    @BeforeClass
    public static void beforeClass() {
        new MockUp<GitlabBuildTrigger.GitlabBuildTriggerDescriptor>() {
            @Mock
            void load() {
            }
        };

        new MockUp<Secret>() {
            @Mock
            Secret fromString(String data) {
                return null;
            }
        };
    }

    @Before
    public void before() throws Exception {

        status.setStatus("running");

        user.setUsername("username");
        mergeRequest.setAssignee(user);

        new NonStrictExpectations() {{
            trigger.getAssigneeFilter();
            result = "";
            trigger.getTagFilter();
            result = "";
        }};

    }

    @Test
    public void build() throws IOException {

        subject.build(cause, new HashMap<String, String>(), project, mergeRequest);

        new Verifications() {{
            trigger.startJob((GitlabCause) any);
        }};
    }

    @Test
    public void doesNotBuild_MergeRequestClosed() throws IOException {

        String stateStr = mergeRequest.getState();
        mergeRequest.setState("closed");

        subject.build(cause, new HashMap<String, String>(), project, mergeRequest);

        new Verifications() {{
            trigger.startJob((GitlabCause) any);
            times = 0;
        }};

        mergeRequest.setState(stateStr);
    }

    @Test
    public void doesNotBuild_MergeRequestMerged() throws IOException {

        String stateStr = mergeRequest.getState();
        mergeRequest.setState("merged");

        subject.build(cause, new HashMap<String, String>(), project, mergeRequest);

        new Verifications() {{
            trigger.startJob((GitlabCause) any);
            times = 0;
        }};

        mergeRequest.setState(stateStr);
    }

    @Test
    public void doesNotBuild_branchPattern() throws IOException {

        new NonStrictExpectations() {{
            trigger.getTargetBranchRegex();
            result = "thisisafakepattern";
        }};

        subject.build(cause, new HashMap<String, String>(), project, mergeRequest);

        new Verifications() {{
            trigger.startJob((GitlabCause) any);
            times = 0;
        }};
    }

    @Test
    public void doesNotBuild_hasCommitStatus() throws IOException {

        new NonStrictExpectations() {{
            api.getCommitStatuses(project, cause.getLastCommitId());
            result = statuses;
        }};

        subject.build(cause, new HashMap<String, String>(), project, mergeRequest);

        new Verifications() {{
            trigger.startJob((GitlabCause) any);
            times = 0;
        }};
    }

    @Test
    public void doesNotBuild_notMatchingAssigneeFilter() throws IOException {

        new NonStrictExpectations() {{
            trigger.getAssigneeFilter();
            result = "jenkins";
        }};

        subject.build(cause, new HashMap<String, String>(), project, mergeRequest);

        new Verifications() {{
            trigger.startJob((GitlabCause) any);
            times = 0;
        }};
    }

    @Test
    public void doesNotBuild_notMatchingTagFilter() throws IOException {

        new NonStrictExpectations() {{
            trigger.getTagFilter();
            result = "Build";
        }};

        subject.build(cause, new HashMap<String, String>(), project, mergeRequest);

        new Verifications() {{
            trigger.startJob((GitlabCause) any);
            times = 0;
        }};
    }

    @Test
    public void onStart_notMuted(@Mocked final AbstractBuild build) {

        new NonStrictExpectations(subject) {{
            invoke(subject, "getCause", (AbstractBuild) build);
            result = cause;

            invoke(subject, "getRootUrl");
            result = "http://git.example.com";

            invoke(subject, "isPublishBuildProgressMessages");
            result = true;
        }};

        subject.onStarted(build);

        new Verifications() {{
            repository.createNote(anyInt, anyString, anyBoolean, anyBoolean);
            times = 1;
        }};
    }

    @Test
    public void onStart_muted(@Mocked final AbstractBuild build) {

        new NonStrictExpectations(subject) {{
            invoke(subject, "getCause", (AbstractBuild) build);
            result = cause;

            invoke(subject, "getRootUrl");
            result = "http://git.example.com";

            invoke(subject, "isPublishBuildProgressMessages");
            result = false;
        }};

        subject.onStarted(build);

        new Verifications() {{
            repository.createNote(anyInt, anyString, anyBoolean, anyBoolean);
            times = 0;
        }};
    }

    @Test
    public void onCompleted_notMuted(@Mocked final AbstractBuild build) {

        new NonStrictExpectations(subject) {{
            invoke(subject, "getCause", (AbstractBuild) build);
            result = cause;

            invoke(subject, "getResult", (AbstractBuild) build);
            result = Result.SUCCESS;

            invoke(subject, "getRootUrl");
            result = "http://git.example.com";

            invoke(subject, "isPublishBuildProgressMessages");
            result = true;
        }};

        subject.onCompleted(build);

        new Verifications() {{
            repository.createNote(anyInt, anyString, anyBoolean, anyBoolean);
            times = 1;
        }};
    }

    @Test
    public void onCompleted_muted(@Mocked final AbstractBuild build) {

        new NonStrictExpectations(subject) {{
            invoke(subject, "getCause", (AbstractBuild) build);
            result = cause;

            invoke(subject, "getResult", (AbstractBuild) build);
            result = Result.SUCCESS;

            invoke(subject, "getRootUrl");
            result = "http://git.example.com";

            invoke(subject, "isPublishBuildProgressMessages");
            result = false;
        }};

        subject.onCompleted(build);

        new Verifications() {{
            repository.createNote(anyInt, anyString, anyBoolean, anyBoolean);
            times = 0;
        }};
    }

    @Test
    public void onCompleted_mutedButBuildFailed(@Mocked final AbstractBuild build) {

        new NonStrictExpectations(subject) {{
            invoke(subject, "getCause", (AbstractBuild) build);
            result = cause;

            invoke(subject, "getResult", (AbstractBuild) build);
            result = Result.FAILURE;

            invoke(subject, "getRootUrl");
            result = "http://git.example.com";

            invoke(subject, "isPublishBuildProgressMessages");
            result = false;
        }};

        subject.onCompleted(build);

        new Verifications() {{
            repository.createNote(anyInt, anyString, anyBoolean, anyBoolean);
            times = 1;
        }};
    }

    @Test
    public void WorkInProgressRespect()
    {
        Assert.assertSame(true, subject.isWorkInProgress("WIP: add some feature, which is not ready yet"));
        Assert.assertSame(false, subject.isWorkInProgress("add some feature, which is not ready yet"));
    }
}
