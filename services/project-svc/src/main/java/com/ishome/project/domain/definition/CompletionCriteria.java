package com.ishome.project.domain.definition;

import java.util.List;

/**
 * 里程碑完成判据——仅简单谓词的结构化集合，全部谓词 AND 关系（"槽位齐 + 产物确认"）。
 *
 * <p>红线：不发明表达式语法。判据超出简单谓词表达力（如 M5 "覆盖已确认空间"）时， 做成 project-svc 服务接口，不扩本结构为 DSL。
 */
public record CompletionCriteria(
    List<SlotCriterion> slotCriteria, List<ArtifactCriterion> artifactCriteria) {

  public static CompletionCriteria none() {
    return new CompletionCriteria(List.of(), List.of());
  }

  /** 空判据 = 不可自动完成（终点里程碑，如 M6 交付）。 */
  public boolean isEmpty() {
    return slotCriteria.isEmpty() && artifactCriteria.isEmpty();
  }
}
