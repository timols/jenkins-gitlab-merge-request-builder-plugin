package org.jenkinsci.plugins.gitlab.models.webhook;

/**
 * Created by lordx_000 on 11/14/2015.
 */
public class PushRepository {
    public String name;
    public String url;
    public String description;
    public String homepage;
    public String git_http_url;
    public String git_ssh_url;

    @Override
    public String toString() {
        return "PushRepository{" +
                "name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", description='" + description + '\'' +
                ", homepage='" + homepage + '\'' +
                ", git_http_url='" + git_http_url + '\'' +
                ", git_ssh_url='" + git_ssh_url + '\'' +
                '}';
    }
}
