package org.jenkinsci.plugins.gitlab;

import javax.servlet.ServletException;
import java.io.IOException;
import hudson.Plugin;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Gitlab Merge Request Plugin
 *
 * @author @timolsh
 */
public final class GitlabMergeRequestPlugin extends Plugin {

    static GitlabMergeRequestPlugin get() {
        return Hudson.getInstance().getPlugin(GitlabMergeRequestPlugin.class);
    }

    @Override
    public void start() throws IOException {
        load();
    }

    @Override
    public void configure(StaplerRequest req, JSONObject formData) throws ServletException, Descriptor.FormException, IOException {
        super.configure(req, formData);
        save();
    }

}

