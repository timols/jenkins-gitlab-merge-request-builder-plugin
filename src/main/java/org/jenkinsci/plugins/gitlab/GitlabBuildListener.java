package org.jenkinsci.plugins.gitlab;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

@Extension
public class GitlabBuildListener extends RunListener<AbstractBuild> {

    @Override
    public void onStarted(AbstractBuild abstractBuild, TaskListener listener) {
        GitlabBuildTrigger trigger = GitlabBuildTrigger.getTrigger(abstractBuild.getProject());

        if (trigger == null) {
            return;
        }

        trigger.getBuilder().getBuilds().onStarted(abstractBuild);
        
    }

    @Override
    public void onCompleted(AbstractBuild abstractBuild, TaskListener listener) {
        GitlabBuildTrigger trigger = GitlabBuildTrigger.getTrigger(abstractBuild.getProject());

        if (trigger == null) {
            return;
        }

        trigger.getBuilder().getBuilds().onCompleted(abstractBuild);
    }
}
