package org.jenkinsci.plugins.gitlab.models.webhook;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Created by lordx_000 on 11/14/2015.
 */
@SuppressFBWarnings("UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD")
public class Push {
    public Integer project_id;
    public String ref;
    public PushRepository repository;

    @Override
    public String toString() {
        return "Push{" +
                "project_id=" + project_id +
                ", ref='" + ref + '\'' +
                ", repository=" + repository +
                '}';
    }
}
