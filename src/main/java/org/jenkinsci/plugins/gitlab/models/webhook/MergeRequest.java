package org.jenkinsci.plugins.gitlab.models.webhook;

/**
 * Created by lordx_000 on 11/14/2015.
 */
public class MergeRequest {
    public ObjectAttributes object_attributes;

    @Override
    public String toString() {
        return "MergeRequest{" +
                "object_attributes=" + object_attributes +
                '}';
    }
}
