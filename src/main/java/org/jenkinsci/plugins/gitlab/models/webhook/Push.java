package org.jenkinsci.plugins.gitlab.models.webhook;

/**
 * Created by lordx_000 on 11/14/2015.
 */
public class Push {
    public Integer project_id;
    public String ref;
    public Repository repository;

    @Override
    public String toString() {
        return "Push{" +
                "project_id=" + project_id +
                ", ref='" + ref + '\'' +
                ", repository=" + repository +
                '}';
    }
}
