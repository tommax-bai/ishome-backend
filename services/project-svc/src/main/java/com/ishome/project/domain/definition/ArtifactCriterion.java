package com.ishome.project.domain.definition;

import com.ishome.project.domain.ArtifactStatus;

/**
 * 简单谓词：指定类型的产物已达到某状态（GENERATED ≤ PRESENTED ≤ CONFIRMED 递进； REJECTED 永不满足）。如 M0.5
 * 愿景图只求送达（PRESENTED，不求确认），M2 布局求 CONFIRMED。
 */
public record ArtifactCriterion(String artifactType, ArtifactStatus requiredStatus) {

  public static ArtifactCriterion presented(String artifactType) {
    return new ArtifactCriterion(artifactType, ArtifactStatus.PRESENTED);
  }

  public static ArtifactCriterion confirmed(String artifactType) {
    return new ArtifactCriterion(artifactType, ArtifactStatus.CONFIRMED);
  }
}
