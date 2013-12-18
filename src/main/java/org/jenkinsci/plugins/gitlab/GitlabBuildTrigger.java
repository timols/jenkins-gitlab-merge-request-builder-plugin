package org.jenkinsci.plugins.gitlab;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import antlr.ANTLRException;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public final class GitlabBuildTrigger extends Trigger<AbstractProject<?, ?>> {
    private static final Logger _logger = Logger.getLogger(GitlabBuildTrigger.class.getName());

    private final String _cron;
    private final String _projectPath;
    transient private GitlabMergeRequestBuilder _gitlabMergeRequestBuilder;

    @DataBoundConstructor
    public GitlabBuildTrigger(String cron, String projectPath) throws ANTLRException {
        super(cron);
        _cron = cron;
        _projectPath = projectPath;
    }

    @Override
    public void start(AbstractProject<?, ?> project, boolean newInstance) {
        try {
            _gitlabMergeRequestBuilder = GitlabMergeRequestBuilder.getBuilder()
                                            .setProject(project)
                                            .setTrigger(this)
                                            .setMergeRequests(DESCRIPTOR.getMergeRequests(project.getFullName()))
                                            .build();
        } catch (IllegalStateException ex) {
            _logger.log(Level.SEVERE, "Can't start trigger", ex);
            return;
        }

        super.start(project, newInstance);
    }

    public QueueTaskFuture<?> startJob(GitlabCause cause) {
        Map<String, ParameterValue> values = getDefaultParameters();

        values.put("gitlabMergeRequestId", new StringParameterValue("gitlabMergeRequestId", String.valueOf(cause.getMergeRequestId())));
        values.put("gitlabMergeRequestIid", new StringParameterValue("gitlabMergeRequestIid", String.valueOf(cause.getMergeRequestIid())));
        values.put("gitlabSourceBranch", new StringParameterValue("gitlabSourceBranch", cause.getSourceBranch()));
        values.put("gitlabTargetBranch", new StringParameterValue("gitlabTargetBranch", cause.getTargetBranch()));

        List<ParameterValue> listValues = new ArrayList<ParameterValue>(values.values());
        return this.job.scheduleBuild2(0, cause, new ParametersAction(listValues));
    }

    private Map<String, ParameterValue> getDefaultParameters() {
        Map<String, ParameterValue> values = new HashMap<String, ParameterValue>();
        ParametersDefinitionProperty definitionProperty = this.job.getProperty(ParametersDefinitionProperty.class);

        if (definitionProperty != null) {
            for (ParameterDefinition definition : definitionProperty.getParameterDefinitions()) {
                values.put(definition.getName(), definition.getDefaultParameterValue());
            }
        }

        return values;
    }

    public GitlabMergeRequestBuilder getBuilder() {
        return _gitlabMergeRequestBuilder;
    }

    @Override
    public void stop() {
        if (_gitlabMergeRequestBuilder != null) {
            _gitlabMergeRequestBuilder.stop();
            _gitlabMergeRequestBuilder = null;
        }

        super.stop();
    }

    @Override
    public void run() {
        _gitlabMergeRequestBuilder.run();
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
        return _projectPath;
    }

    @Extension
    public static final GitlabBuildTriggerDescriptor DESCRIPTOR = new GitlabBuildTriggerDescriptor();

    public static final class GitlabBuildTriggerDescriptor extends TriggerDescriptor {
        private String _botUsername = "jenkins";
        private String _gitlabHostUrl;
        private String _botApiToken;
        private String _cron = "*/5 * * * *";
        private boolean _enableBuildTriggeredMessage = true;
        private String _successMessage = "Build finished.  Tests PASSED.";
        private String _unstableMessage = "Build finished.  Tests FAILED.";
        private String _failureMessage = "Build finished.  Tests FAILED.";
        private boolean _ignoreCertificateErrors = false;

        private transient Gitlab _gitlab;
        private Map<String, Map<Integer, GitlabMergeRequestWrapper>> _jobs;

        public GitlabBuildTriggerDescriptor() {
            load();
            if (_jobs == null) {
                _jobs = new HashMap<String, Map<Integer, GitlabMergeRequestWrapper>>();
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
            _botUsername = formData.getString("botUsername");
            _botApiToken = formData.getString("botApiToken");
            _gitlabHostUrl = formData.getString("gitlabHostUrl");
            _cron = formData.getString("cron");
            _enableBuildTriggeredMessage = formData.getBoolean("enableBuildTriggeredMessage");
            _successMessage = formData.getString("successMessage");
            _unstableMessage = formData.getString("unstableMessage");
            _failureMessage = formData.getString("failureMessage");
            _ignoreCertificateErrors = formData.getBoolean("ignoreCertificateErrors");

            save();

            _gitlab = new Gitlab();
            _gitlab.get().ignoreCertificateErrors(_ignoreCertificateErrors);

            return super.configure(req, formData);
        }

        public FormValidation doCheckCron(@QueryParameter String value){
            return (new TimerTrigger.DescriptorImpl().doCheckSpec(value));
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
            return _cron;
        }

        public boolean isEnableBuildTriggeredMessage() {
        	return _enableBuildTriggeredMessage;
        }

        public String getSuccessMessage() {
            if (_successMessage == null) {
                _successMessage = "Build finished.  Tests PASSED.";
            }
            return _successMessage;
        }

        public String getUnstableMessage() {
            if (_unstableMessage == null) {
                _unstableMessage = "Build finished.  Tests FAILED.";
            }
            return _unstableMessage;
        }

        public String getFailureMessage() {
            if (_failureMessage == null) {
                _failureMessage = "Build finished.  Tests FAILED.";
            }
            return _failureMessage;
        }

        public Gitlab getGitlab() {
            if (_gitlab == null) {
                _gitlab = new Gitlab();
                _gitlab.get().ignoreCertificateErrors(_ignoreCertificateErrors);
            }
            return _gitlab;
        }

        public boolean isIgnoreCertificateErrors() {
            return _ignoreCertificateErrors;
        }

        public Map<Integer, GitlabMergeRequestWrapper> getMergeRequests(String projectName) {
            Map<Integer, GitlabMergeRequestWrapper> result;

            if (_jobs.containsKey(projectName)) {
                result = _jobs.get(projectName);
            } else {
                result = new HashMap<Integer, GitlabMergeRequestWrapper>();
                _jobs.put(projectName, result);
            }

            return result;
        }

        public String getBotApiToken() {
            return _botApiToken;
        }

        public String getGitlabHostUrl() {
            return _gitlabHostUrl;
        }

        public String getBotUsername() {
            return _botUsername;
        }

    }

}
