package com.ishome.project.application;

/** 按属主取或建项目的结果：created=true 表示这一次新建（并已进入首个里程碑）。 */
public record ProjectFindOrCreateResult(
    String projectId, String currentMilestone, String processVersion, boolean created) {}
