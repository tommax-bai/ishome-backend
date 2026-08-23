package com.ishome.project.application;

import java.util.List;

/**
 * 一次业务事实处理后的里程碑推进结果。判据满足时可能连续迁移多个里程碑 （enteredMilestones 按序），createdTaskIds = 途经 on_enter 创建的生成任务。
 */
public record MilestoneProgressResult(
    String projectId,
    String currentMilestone,
    boolean transitioned,
    List<String> enteredMilestones,
    List<String> createdTaskIds) {}
