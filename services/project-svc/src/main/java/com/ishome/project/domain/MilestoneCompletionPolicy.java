package com.ishome.project.domain;

import com.ishome.project.domain.definition.ArtifactCriterion;
import com.ishome.project.domain.definition.CompletionCriteria;
import com.ishome.project.domain.definition.MilestoneDefinition;
import com.ishome.project.domain.definition.ProcessDefinition;
import com.ishome.project.domain.definition.SlotCriterion;
import java.util.Collection;
import java.util.Optional;

/**
 * 里程碑引擎核心（事件驱动，真相在表）：每处理完影响判据的事件并落库后调用 checkCompletion—— 读判据配置 → 对 slot/artifact 表现状布尔求值 → true
 * 则判定迁移并返回目标里程碑的 on_enter 动作。
 *
 * <p>纯函数：入参即全部事实（当前里程碑 + 槽位/产物现状 + 流程定义），无 IO 无副作用； 迁移落库与 on_enter 执行在 application 层。单点判定，chat
 * 永不判里程碑；不搞定时轮询。
 */
public final class MilestoneCompletionPolicy {

  /**
   * 判定当前里程碑是否完成。完成且存在后继里程碑时返回迁移；否则为空。
   *
   * <p>空判据（如 M6 交付）视为不可自动完成——终点里程碑不因"零条件"而空转迁移。
   */
  public Optional<MilestoneTransition> checkCompletion(
      ProcessDefinition definition,
      String currentMilestoneId,
      Collection<Slot> slots,
      Collection<Artifact> artifacts) {
    MilestoneDefinition current =
        definition
            .findMilestone(currentMilestoneId)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "里程碑 " + currentMilestoneId + " 不在流程定义 " + definition.version() + " 内"));

    if (!isSatisfied(current.completionCriteria(), slots, artifacts)) {
      return Optional.empty();
    }
    return definition
        .findNextMilestone(currentMilestoneId)
        .map(next -> new MilestoneTransition(currentMilestoneId, next.id(), next.onEnterActions()));
  }

  private boolean isSatisfied(
      CompletionCriteria criteria, Collection<Slot> slots, Collection<Artifact> artifacts) {
    if (criteria.isEmpty()) {
      return false;
    }
    boolean slotsSatisfied =
        criteria.slotCriteria().stream().allMatch(criterion -> isSlotSatisfied(criterion, slots));
    boolean artifactsSatisfied =
        criteria.artifactCriteria().stream()
            .allMatch(criterion -> isArtifactSatisfied(criterion, artifacts));
    return slotsSatisfied && artifactsSatisfied;
  }

  private boolean isSlotSatisfied(SlotCriterion criterion, Collection<Slot> slots) {
    return slots.stream()
        .anyMatch(
            slot ->
                slot.slotKey().equals(criterion.slotKey())
                    && slot.value() != null
                    && !slot.value().isBlank()
                    && (criterion.acceptedStates().isEmpty()
                        || criterion.acceptedStates().contains(slot.cognitiveState())));
  }

  private boolean isArtifactSatisfied(ArtifactCriterion criterion, Collection<Artifact> artifacts) {
    return artifacts.stream()
        .anyMatch(
            artifact ->
                artifact.artifactType().equals(criterion.artifactType())
                    && reaches(artifact.status(), criterion.requiredStatus()));
  }

  /** 状态递进：GENERATED ≤ PRESENTED ≤ CONFIRMED；REJECTED 永不满足任何要求。 */
  private boolean reaches(ArtifactStatus actual, ArtifactStatus required) {
    return switch (required) {
      case GENERATED ->
          actual == ArtifactStatus.GENERATED
              || actual == ArtifactStatus.PRESENTED
              || actual == ArtifactStatus.CONFIRMED;
      case PRESENTED -> actual == ArtifactStatus.PRESENTED || actual == ArtifactStatus.CONFIRMED;
      case CONFIRMED -> actual == ArtifactStatus.CONFIRMED;
      case REJECTED -> false;
    };
  }
}
