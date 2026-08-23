package com.ishome.project.domain.definition;

import java.util.List;
import java.util.Optional;

/**
 * 版本化流程定义：有序里程碑序列（沟通助手方案 §7：M0-M6）。
 *
 * <p>{@code process_version} 在项目创建时固化到 projects 表，配置演进只影响新项目； 存量迁移是显式数据操作（D10）。
 */
public record ProcessDefinition(String version, List<MilestoneDefinition> milestones) {

  public MilestoneDefinition firstMilestone() {
    if (milestones.isEmpty()) {
      throw new IllegalStateException("流程定义 " + version + " 不含任何里程碑");
    }
    return milestones.get(0);
  }

  public Optional<MilestoneDefinition> findMilestone(String milestoneId) {
    return milestones.stream().filter(m -> m.id().equals(milestoneId)).findFirst();
  }

  /** 序列中紧随其后的里程碑；当前已是终点时为空。 */
  public Optional<MilestoneDefinition> findNextMilestone(String milestoneId) {
    for (int i = 0; i < milestones.size() - 1; i++) {
      if (milestones.get(i).id().equals(milestoneId)) {
        return Optional.of(milestones.get(i + 1));
      }
    }
    return Optional.empty();
  }
}
