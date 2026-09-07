package com.ishome.project.application;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次业务事实处理后的里程碑推进结果。判据满足时可能连续迁移多个里程碑 （enteredMilestones 按序），createdTaskIds = 本次为这个项目铸出的生成任务。
 *
 * <p>createdTaskIds 里既有迁移途经 on_enter 建出的，也有补派建出的（不迁移、图没出来、业主重发户型图那条路， 用户裁决
 * 2026-09-07）。契约字段名与形态不变（project.v1 {@code created_task_ids} 仍是任务 id 列表）， 会话侧照旧只拿它做日志与排错，不按来路分支。
 */
public record MilestoneProgressResult(
    String projectId,
    String currentMilestone,
    boolean transitioned,
    List<String> enteredMilestones,
    List<String> createdTaskIds) {

  /** 并进补派铸出的任务 id（顺序在 on_enter 建出的之后）。 */
  MilestoneProgressResult withAdditionalTaskIds(List<String> additionalTaskIds) {
    if (additionalTaskIds.isEmpty()) {
      return this;
    }
    List<String> merged = new ArrayList<>(createdTaskIds);
    merged.addAll(additionalTaskIds);
    return new MilestoneProgressResult(
        projectId, currentMilestone, transitioned, enteredMilestones, List.copyOf(merged));
  }
}
