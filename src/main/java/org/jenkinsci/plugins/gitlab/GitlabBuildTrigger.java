package org.jenkinsci.plugins.gitlab;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.model.*;
import hudson.model.queue.QueueTaskFuture;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import hudson.util.Secret;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class GitlabBuildTrigger extends Trigger<AbstractProject<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(GitlabBuildTrigger.class.getName());

    private final String projectPath;
    private final String targetBranchRegex;
    private final boolean useHttpUrl;
    private final String assigneeFilter;
    private final String tagFilter;
    private final String triggerComment;
    private final boolean autoCloseFailed;
    private final boolean autoMergePassed;
    transient private GitlabMergeRequestBuilder builder;

    @DataBoundConstructor
    public GitlabBuildTrigger(String cron,
                              String projectPath,
                              String targetBranchRegex,
                              boolean useHttpUrl,
                              String assigneeFilter,
                              String tagFilter,
                              String triggerComment,
                              boolean autoCloseFailed,
                              boolean autoMergePassed) throws ANTLRException {

        super(cron);
        this.projectPath = projectPath;
        this.targetBranchRegex = targetBranchRegex;
        this.useHttpUrl = useHttpUrl;
        this.assigneeFilter = assigneeFilter;
        this.tagFilter = tagFilter;
        this.triggerComment = triggerComment;
        this.autoCloseFailed = autoCloseFailed;
        this.autoMergePassed = autoMergePassed;
    }

    @Override
    public void start(AbstractProject<?, ?> project, boolean newInstance) {
        try {
            GitlabWebhooks.setTrigger(this);

            builder = GitlabMergeRequestBuilder.getBuilder()
                    .setProject(project)
                    .setTrigger(this)
                    .setMergeRequests(DESCRIPTOR.getMergeRequests(project.getFullName()))
                    .build();
        } catch (IllegalStateException ex) {
            LOGGER.log(Level.SEVERE, "Can't start trigger", ex);
            return;
        }

        super.start(project, newInstance);
    }

    public QueueTaskFuture<?> startJob(GitlabCause cause) {
        Map<String, ParameterValue> values = getDefaultParameters();

        values.put("gitlabMergeRequestId", new StringParameterValue("gitlabMergeRequestId", String.valueOf(cause.getMergeRequestId())));
        values.put("gitlabMergeRequestIid", new StringParameterValue("gitlabMergeRequestIid", String.valueOf(cause.getMergeRequestIid())));
        values.put("gitlabSourceName", new StringParameterValue("gitlabSourceName", cause.getSourceName()));
        values.put("gitlabSourceRepository", new StringParameterValue("gitlabSourceRepository", cause.getSourceRepository()));
        values.put("gitlabSourceBranch", new StringParameterValue("gitlabSourceBranch", cause.getSourceBranch()));
        values.put("gitlabTargetBranch", new StringParameterValue("gitlabTargetBranch", cause.getTargetBranch()));
        values.put("gitlabDescription", new StringParameterValue("gitlabDescription", cause.getDescription()));
        for (Map.Entry<String, String> entry : cause.getCustomParameters().entrySet()) {
            values.put(entry.getKey(), new StringParameterValue(entry.getKey(), entry.getValue()));
        }

        List<ParameterValue> listValues = new ArrayList<>(values.values());
        return job.scheduleBuild2(0, cause, new ParametersAction(listValues));
    }

    private Map<String, ParameterValue> getDefaultParameters() {
        Map<String, ParameterValue> values = new HashMap<>();
        ParametersDefinitionProperty definitionProperty = job.getProperty(ParametersDefinitionProperty.class);

        if (definitionProperty != null) {
            for (ParameterDefinition definition : definitionProperty.getParameterDefinitions()) {
                values.put(definition.getName(), definition.getDefaultParameterValue());
            }
        }

        return values;
    }

    public GitlabMergeRequestBuilder getBuilder() {
        return builder;
    }

    @Override
    public void stop() {
        if (job == null) {
            return;
        }


        if (builder != null) {
            builder.stop();
            builder = null;
        }

        super.stop();
    }

    @Override
    public void run() {
        if (job == null) {
            return;
        }

        if (builder != null) {
            builder.run();
        }
        DESCRIPTOR.save();
    }

    @Override
    public GitlabBuildTriggerDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public static GitlabBuildTriggerDescriptor getDesc() {
        return DESCRIPTOR;
    }

    public static GitlabBuildTrigger getTrigger(AbstractProject project) {
        Trigger trigger = project.getTrigger(GitlabBuildTrigger.class);

        if (trigger == null || !(trigger instanceof GitlabBuildTrigger)) {
            return null;
        }

        return (GitlabBuildTrigger) trigger;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public String getTargetBranchRegex() {
        return targetBranchRegex;
    }

    public boolean getUseHttpUrl() {
        return useHttpUrl;
    }

    public String getAssigneeFilter() {
        return assigneeFilter;
    }
    
    public String getTagFilter() {
		return tagFilter;
	}

    public String getTriggerComment() {
        return triggerComment;
    }

    public boolean getAutoCloseFailed() {
        return autoCloseFailed;
    }

    public boolean getAutoMergePassed() {
        return autoMergePassed;
    }

    @Extension
    public static final GitlabBuildTriggerDescriptor DESCRIPTOR = new GitlabBuildTriggerDescriptor();

    public static final class GitlabBuildTriggerDescriptor extends TriggerDescriptor {
        private String botUsername = "jenkins";
        private String gitlabHostUrl;
        private String assigneeFilter = "jenkins";
        private String tagFilter = "Build";
        @Deprecated
        private String botApiToken;
        private Secret botApiTokenSecret;
        private String cron = "H/5 * * * *";
        private boolean enableBuildTriggeredMessage = true;
        private String successMessage = "Build finished.  Tests PASSED.";
        private String unstableMessage = "Build finished.  Tests FAILED.";
        private String failureMessage = "Build finished.  Tests FAILED.";
        private boolean ignoreCertificateErrors = false;

        private transient Gitlab gitlab;
        private Map<String, Map<Integer, GitlabMergeRequestWrapper>> jobs;

        public GitlabBuildTriggerDescriptor() {
            load();
            if (jobs == null) {
                jobs = new HashMap<>();
            }
            if (botApiTokenSecret == null) {
                botApiTokenSecret = Secret.fromString(botApiToken);
                botApiToken = null;
            }
        }

        @Override
        public boolean isApplicable(Item item) {
            return item instanceof AbstractProject;
        }

        @Override
        public String getDisplayName() {
            return "Gitlab Merge Requests Builder";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            botUsername = formData.getString("botUsername");
            botApiTokenSecret = Secret.fromString(formData.getString("botApiTokenSecret"));
            gitlabHostUrl = formData.getString("gitlabHostUrl");
            cron = formData.getString("cron");
            assigneeFilter = formData.getString("assigneeFilter");
            tagFilter = formData.getString("tagFilter");
            enableBuildTriggeredMessage = formData.getBoolean("enableBuildTriggeredMessage");
            successMessage = formData.getString("successMessage");
            unstableMessage = formData.getString("unstableMessage");
            failureMessage = formData.getString("failureMessage");
            ignoreCertificateErrors = formData.getBoolean("ignoreCertificateErrors");

            save();

            gitlab = new Gitlab();

            return super.configure(req, formData);
        }

        public FormValidation doCheckGitlabHostUrl(@QueryParameter String value) {
            if (!value.isEmpty()) {
                return FormValidation.ok();
            }

            return FormValidation.error("Gitlab Host Url needs to be set");
        }

        public FormValidation doCheckBotUsername(@QueryParameter String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.error("You must provide a username for the Jenkins user");
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckBotApiToken(@QueryParameter String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.error("You must provide an API token for the Jenkins user");
            }

            return FormValidation.ok();
        }

        public String getCron() {
            return cron;
        }

        public String getAssigneeFilter() {
            return assigneeFilter;
        }
        
        public String getTagFilter() {
			return tagFilter;
		}

		public boolean isEnableBuildTriggeredMessage() {
            return enableBuildTriggeredMessage;
        }

        public String getSuccessMessage() {
            if (successMessage == null) {
                successMessage = "Build finished.  Tests PASSED.";
            }
            return successMessage;
        }

        public String getUnstableMessage() {
            if (unstableMessage == null) {
                unstableMessage = "Build finished.  Tests FAILED.";
            }
            return unstableMessage;
        }

        public String getFailureMessage() {
            if (failureMessage == null) {
                failureMessage = "Build finished.  Tests FAILED.";
            }
            return failureMessage;
        }

        public Gitlab getGitlab() {
            if (gitlab == null) {
                gitlab = new Gitlab();
            }
            return gitlab;
        }

        public boolean isIgnoreCertificateErrors() {
            return ignoreCertificateErrors;
        }

		public Map<Integer, GitlabMergeRequestWrapper> getMergeRequests(String projectName) {
            Map<Integer, GitlabMergeRequestWrapper> result;

            if (jobs.containsKey(projectName)) {
                result = jobs.get(projectName);
            } else {
                result = new HashMap<Integer, GitlabMergeRequestWrapper>();
                jobs.put(projectName, result);
            }

            return result;
        }

        /**
         * @deprecated use {@link #getBotApiTokenSecret()}.
         */
        @Deprecated
        public String getBotApiToken() {
            return botApiTokenSecret.getPlainText();
        }

        public Secret getBotApiTokenSecret() {
            return botApiTokenSecret;
        }

        public String getGitlabHostUrl() {
            return gitlabHostUrl;
        }

        public String getBotUsername() {
            return botUsername;
        }

    }

}
