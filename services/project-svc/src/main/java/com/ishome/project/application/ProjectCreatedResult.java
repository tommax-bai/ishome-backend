package com.ishome.project.application;

import java.util.List;

/** 建项目结果：初始里程碑 = 流程定义首个里程碑，createdTaskIds = 其 on_enter 建的任务。 */
public record ProjectCreatedResult(
    String projectId,
    String currentMilestone,
    String processVersion,
    List<String> createdTaskIds) {}
