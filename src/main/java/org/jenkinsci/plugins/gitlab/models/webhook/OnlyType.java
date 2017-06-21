package org.jenkinsci.plugins.gitlab.models.webhook;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * Created by lordx_000 on 11/14/2015.
 */
public class OnlyType {
    public String object_kind;

    public static OnlyType fromJson(String jsonString) throws JsonSyntaxException {
        Gson g = new GsonBuilder().setPrettyPrinting().create();
        return g.fromJson(jsonString, OnlyType.class);
    }

}
