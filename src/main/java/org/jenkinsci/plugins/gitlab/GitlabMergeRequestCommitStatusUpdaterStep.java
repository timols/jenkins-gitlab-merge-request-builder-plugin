package org.jenkinsci.plugins.gitlab;
/*
 * <summary></summary>
 * <author>Edward Chen</author>
 * <email>edward_chen@gemdata.net</email>
 * <create-date>2019-07-22 14:56</create-date>
 *
 * Copyright (c) 2016-2018, GemData. All Right Reserved, http://www.gemdata.net
 */

import java.util.EnumSet;
import java.util.Set;

import org.jenkinsci.plugins.gitlab.models.webhook.BuildState;
import org.jenkinsci.plugins.workflow.steps.*;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.export.ExportedBean;

import com.google.common.collect.ImmutableSet;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;

@ExportedBean
public class GitlabMergeRequestCommitStatusUpdaterStep extends Step {

    private String name;
    private BuildState state;
    private String url;

    @DataBoundConstructor
    public GitlabMergeRequestCommitStatusUpdaterStep(String name, BuildState state, String url) {
        this.name = StringUtils.isEmpty(name) ? null : name;
        this.state = state;
        this.url = url;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new UpdateGitLabCommitStatusStepExecution(context, this);
    }

    public String getName() {
        return name;
    }

    @DataBoundSetter
    public void setName(String name) {
        this.name = StringUtils.isEmpty(name) ? null : name;
    }

    public BuildState getState() {
        return state;
    }

    @DataBoundSetter
    public void setState(BuildState state) {
        this.state = state;
    }

    public String getUrl() {
        return url;
    }

    @DataBoundSetter
    public void setUrl(String sonarqubeUrl) {
        this.url = sonarqubeUrl;
    }

    public static class UpdateGitLabCommitStatusStepExecution extends SynchronousStepExecution<Void> {
        private static final long serialVersionUID = 1;

        private final transient Run<?, ?> run;

        private final transient GitlabMergeRequestCommitStatusUpdaterStep step;

        UpdateGitLabCommitStatusStepExecution(StepContext context, GitlabMergeRequestCommitStatusUpdaterStep step) throws Exception {
            super(context);
            this.step = step;
            run = context.get(Run.class);
        }

        @Override
        protected Void run() throws Exception {
            final String name = StringUtils.isEmpty(step.name) ? "jenkins" : step.name;
            Gitlab gitlab = GitlabBuildTrigger.DESCRIPTOR.getGitlab();
            GitlabCause gitlabCause = run.getCause(GitlabCause.class);
            if (gitlabCause != null) {
                gitlab.changeCommitStatus(gitlabCause.getTargetProjectId(), gitlabCause.getSourceBranch(), gitlabCause.getLastCommitId(), name, "", step.state.toString(), step.url);
            }
            return null;
        }
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {
        @Override
        public String getDisplayName() {
            return "[GitlabMRBuilder] Update the merge request latest commit status in GitLab";
        }

        @Override
        public String getFunctionName() {
            return "updateGitlabMRCommitStatus";
        }

        public ListBoxModel doFillStateItems() {
            ListBoxModel options = new ListBoxModel();
            for (BuildState buildState : EnumSet.allOf(BuildState.class)) {
                options.add(buildState.name());
            }
            return options;
        }

        @Override
        public Set<Class<?>> getRequiredContext() {
            return ImmutableSet.of(TaskListener.class, Run.class);
        }
    }
}
