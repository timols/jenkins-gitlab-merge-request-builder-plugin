package org.jenkinsci.plugins.gitlab.models.webhook;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Created by lordx_000 on 11/14/2015.
 */
@SuppressFBWarnings("UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD")
public class MergeRequestRepository {
    public String name;
    public String http_url;
    public String ssh_url;

    @Override
    public String toString() {
        return "MergeRequestRepository{" +
                "name='" + name + '\'' +
                ", http_url='" + http_url + '\'' +
                ", ssh_url='" + ssh_url + '\'' +
                '}';
    }
}
