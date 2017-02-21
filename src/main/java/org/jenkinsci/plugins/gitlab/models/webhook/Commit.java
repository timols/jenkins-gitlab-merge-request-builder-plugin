package org.jenkinsci.plugins.gitlab.models.webhook;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Created by lordx_000 on 11/15/2015.
 */
@SuppressFBWarnings({"UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD", "UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD"})
public class Commit {
    public String id;
    public String message;
    public String timestamp;
    public String url;
}
