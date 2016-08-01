package org.jenkinsci.plugins.gitlab;

import hudson.Extension;
import hudson.security.csrf.CrumbExclusion;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by bigmichi1 on 28.07.16.
 */
@Extension
public class GitlabWebhookCrumbExclusion extends CrumbExclusion {
	@Override
	public boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
		String pathInfo = request.getPathInfo();
		if (pathInfo != null && pathInfo.startsWith("/" + GitlabWebhooks.URLNAME + "/")) {
			chain.doFilter(request, response);
			return true;
		}
		return false;
	}
}
