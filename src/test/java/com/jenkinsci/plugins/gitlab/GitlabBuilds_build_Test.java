package com.jenkinsci.plugins.gitlab;

import hudson.util.Secret;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import mockit.Injectable;
import mockit.Mock;
import mockit.MockUp;
import mockit.NonStrictExpectations;
import mockit.Tested;
import mockit.Verifications;
import mockit.integration.junit4.JMockit;

import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabCommitStatus;
import org.gitlab.api.models.GitlabMergeRequest;
import org.gitlab.api.models.GitlabProject;
import org.gitlab.api.models.GitlabUser;
import org.jenkinsci.plugins.gitlab.GitlabBuilds;
import org.jenkinsci.plugins.gitlab.GitlabCause;
import org.jenkinsci.plugins.gitlab.GitlabRepository;
import org.jenkinsci.plugins.gitlab.GitlabBuildTrigger;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMockit.class)
public class GitlabBuilds_build_Test {
	@Injectable GitlabAPI api;
	@Injectable GitlabProject project;
	@Injectable GitlabUser user;
	@Injectable GitlabMergeRequest mergeRequest;

	@Injectable GitlabBuildTrigger trigger;
	@Injectable GitlabRepository repository;

	@Tested GitlabBuilds subject;

	GitlabCause cause = new GitlabCause(
		1,
		2,
		"sourceName",
		"sourceRepo",
		"sourceBranch",
		"targetBranch",
		new HashMap<String, String>(),
		"description",
		3,
		4, 
		"commitHash"
	);

	List<GitlabCommitStatus> statuses = Arrays.asList( new GitlabCommitStatus() );

	@BeforeClass
  public static void beforeClass() {
		new MockUp<GitlabBuildTrigger.GitlabBuildTriggerDescriptor>() {
      @Mock void load() {}
    };

    new MockUp<Secret>() {
      @Mock Secret fromString(String data) { return null; }
    };
	}

	@Before
  public void before() throws Exception {

		user.setUsername("username");
		mergeRequest.setAssignee(user);

		new NonStrictExpectations() {{
        trigger.getAssigneeFilter(); result = "";
        trigger.getTagFilter(); result = "";
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
  public void doesNotBuild_branchPattern() throws IOException {

		new NonStrictExpectations() {{
        trigger.getTargetBranchRegex(); result = "thisisafakepattern";
    }};

		subject.build(cause, new HashMap<String, String>(), project, mergeRequest);

		new Verifications() {{
			trigger.startJob((GitlabCause) any); times = 0;
		}};
	}

	@Test
  public void doesNotBuild_hasCommitStatus() throws IOException {

		new NonStrictExpectations() {{
        api.getCommitStatuses(project, cause.getLastCommitId()); result = statuses;
    }};

		subject.build(cause, new HashMap<String, String>(), project, mergeRequest);

		new Verifications() {{
			trigger.startJob((GitlabCause) any); times = 0;
		}};
	}

	@Test
  public void doesNotBuild_notMatchingAssigneeFilter() throws IOException {

		new NonStrictExpectations() {{
			trigger.getAssigneeFilter(); result = "jenkins";
    }};

		subject.build(cause, new HashMap<String, String>(), project, mergeRequest);

		new Verifications() {{
			trigger.startJob((GitlabCause) any); times = 0;
		}};
	}

	@Test
  public void doesNotBuild_notMatchingTagFilter() throws IOException {

		new NonStrictExpectations() {{
			trigger.getTagFilter(); result = "Build";
     }};

		subject.build(cause, new HashMap<String, String>(), project, mergeRequest);

		new Verifications() {{
			trigger.startJob((GitlabCause) any); times = 0;
		}};
	}

}
