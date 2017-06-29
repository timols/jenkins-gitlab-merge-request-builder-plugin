package org.jenkinsci.plugins.gitlab.models.webhook;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import jnr.ffi.annotations.In;

/**
 * Created by lordx_000 on 11/14/2015.
 */
public class MergeRequest {
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
    public MergeRequestObjectAttributes object_attributes;


    @Override
    public String toString() {
        return String.format("MergeRequest: id %s, iid %s, target %s", this.getId(), this.getIid(), this.getTarget());
    }

    public static MergeRequest fromJson(String jsonString) throws JsonSyntaxException {
        Gson g = new GsonBuilder().setPrettyPrinting().create();
        return g.fromJson(jsonString, MergeRequest.class);
    }

    public Integer getId() {
        Integer result = this.id != null ? this.id :this.object_attributes.id;
        return result;
    }

    public Integer getIid() {
        Integer result = this.iid != null ? this.iid : this.object_attributes.iid;
        return result;
    }

    public MergeRequestRepository getSource() {
        MergeRequestRepository result = this.source != null ? this.source : this.object_attributes.source;
        return result;
    }

    public MergeRequestRepository getTarget() {
        MergeRequestRepository result = this.target != null ? this.target : this.object_attributes.target;
        return result;
    }

    public String getTitle() {
        String result = this.title != null ? this.title : this.object_attributes.title;
        return result;
    }

    public String getDescription() {
        String result = this.description != null ? this.description : this.object_attributes.description;
        return result;
    }

    public Commit getLast_commit() {
        Commit result = this.last_commit != null ? this.last_commit : this.object_attributes.last_commit;
        return result;
    }

    public Integer getTarget_project_id() {
        Integer result = this.target_project_id != null ? this.target_project_id : this.object_attributes.target_project_id;
        return result;
    }
}
