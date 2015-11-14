package org.jenkinsci.plugins.gitlab.models.webhook;

/**
 * Created by lordx_000 on 11/14/2015.
 */
public class ObjectAttributes {
    public String action;

    public Integer id;
    public Integer iid;
    public String target_branch;
    public Integer target_project_id;
    public MergeRequestRepository target;
    public String source_branch;
    public Integer source_project_id;
    public MergeRequestRepository source;

    @Override
    public String toString() {
        return "ObjectAttributes{" +
                "action='" + action + '\'' +
                ", id=" + id +
                ", iid=" + iid +
                ", target_branch='" + target_branch + '\'' +
                ", target_project_id=" + target_project_id +
                ", target=" + target +
                ", source_branch='" + source_branch + '\'' +
                ", source_project_id=" + source_project_id +
                ", source=" + source +
                '}';
    }
}
