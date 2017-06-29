package org.jenkinsci.plugins.gitlab.models.webhook;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Created by lordx_000 on 11/14/2015.
 */
@SuppressFBWarnings("UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD")
public class MergeRequestObjectAttributes {
    public String action;
    public Integer id;
    public Integer iid;
    public String target_branch;
    public Integer target_project_id;
    public MergeRequestRepository target;
    public String source_branch;
    public Integer source_project_id;
    public MergeRequestRepository source;
    public Commit last_commit;
    public String title;
    public String description;


    @Override
    public String toString() {
        return "MergeRequestObjectAttributes{" +
                "action='" + action + '\'' +
                ", id=" + id +
                ", iid=" + iid +
                ", title='" + title + '\'' +
                ", target_branch='" + target_branch + '\'' +
                ", target_project_id=" + target_project_id +
                ", target=" + target +
                ", source_branch='" + source_branch + '\'' +
                ", source_project_id=" + source_project_id +
                ", source=" + source +
                ", last_commit=" + last_commit +
                '}';
    }
}
