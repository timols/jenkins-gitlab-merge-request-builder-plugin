package org.jenkinsci.plugins.gitlab.models.webhook;
/*
 * <summary></summary>
 * <author>Edward Chen</author>
 * <email>edward_chen@gemdata.net</email>
 * <create-date>2019-07-22 15:04</create-date>
 *
 * Copyright (c) 2016-2018, GemData. All Right Reserved, http://www.gemdata.net
 */

public enum BuildState {
    pending, running, canceled, success, failed
}
