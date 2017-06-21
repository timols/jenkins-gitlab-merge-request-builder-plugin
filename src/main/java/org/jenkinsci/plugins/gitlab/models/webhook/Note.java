package org.jenkinsci.plugins.gitlab.models.webhook;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * Created by mkoshel on 6/20/17.
 */
public class Note {
    public MergeRequest merge_request;

    public static Note fromJson(String jsonString) throws JsonSyntaxException {
        Gson g = new GsonBuilder().setPrettyPrinting().create();
        return g.fromJson(jsonString, Note.class);
    }
}
