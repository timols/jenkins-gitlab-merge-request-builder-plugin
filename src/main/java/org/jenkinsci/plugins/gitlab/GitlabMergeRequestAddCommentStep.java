package org.jenkinsci.plugins.gitlab;
/*
 * <summary></summary>
 * <author>Edward Chen</author>
 * <email>edward_chen@gemdata.net</email>
 * <create-date>2019-07-22 14:56</create-date>
 *
 * Copyright (c) 2016-2018, GemData. All Right Reserved, http://www.gemdata.net
 */

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.gitlab.models.webhook.BuildState;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Logger;

@ExportedBean
public class GitlabMergeRequestAddCommentStep extends Step {

    private String comment;

    @DataBoundConstructor
    public GitlabMergeRequestAddCommentStep(String comment) {
        this.comment = StringUtils.isEmpty(comment) ? null : comment;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new AddGitLabCommentStepExecution(context, this);
    }

    public String getComment() {
        return comment;
    }

    @DataBoundSetter
    public void setComment(String comment) {
        this.comment = StringUtils.isEmpty(comment) ? null : comment;
    }


    public static class AddGitLabCommentStepExecution extends SynchronousStepExecution<Void> {
        private static final Logger LOGGER = Logger.getLogger(GitlabWebhooks.class.getName());

        private static final long serialVersionUID = 1;

        private final transient Run<?, ?> run;

        private final transient GitlabMergeRequestAddCommentStep step;

        AddGitLabCommentStepExecution(StepContext context, GitlabMergeRequestAddCommentStep step) throws Exception {
            super(context);
            this.step = step;
            run = context.get(Run.class);
        }

        @Override
        protected Void run() throws Exception {
            final String comment = StringUtils.isEmpty(step.comment) ? "" : step.comment;
            GitlabCause gitlabCause = run.getCause(GitlabCause.class);
            if (gitlabCause != null) {
                LOGGER.info(String.format("Add comment for mr %d:%d:%d", gitlabCause.getMergeRequestId(), gitlabCause.getMergeRequestIid(), gitlabCause.getSourceProjectId()));
                GitlabMergeRequestWrapper.createNote(gitlabCause.getMergeRequestId(),
                    gitlabCause.getMergeRequestIid(),
                    gitlabCause.getSourceProjectId(),
                    comment,
                    false,
                    false);
            }

            return null;
        }
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {
        @Override
        public String getDisplayName() {
            return "[GitlabMRBuilder] Add comment to the merge request in GitLab";
        }

        @Override
        public String getFunctionName() {
            return "addGitlabMRNote";
        }

        @Override
        public Set<Class<?>> getRequiredContext() {
            return ImmutableSet.of(TaskListener.class, Run.class);
        }
    }
}
