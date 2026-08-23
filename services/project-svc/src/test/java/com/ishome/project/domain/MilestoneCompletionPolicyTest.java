package com.ishome.project.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ishome.project.domain.definition.ArtifactCriterion;
import com.ishome.project.domain.definition.CompletionCriteria;
import com.ishome.project.domain.definition.MilestoneDefinition;
import com.ishome.project.domain.definition.OnEnterAction;
import com.ishome.project.domain.definition.ProcessDefinition;
import com.ishome.project.domain.definition.SlotCriterion;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** checkCompletion 布尔求值与迁移判定：判据满足/不满足/部分满足/终点空判据。 */
class MilestoneCompletionPolicyTest {

  private static final String PROJECT_ID = "p-1";

  private final MilestoneCompletionPolicy policy = new MilestoneCompletionPolicy();

  /** 测试定义：MA（槽位齐 + 槽位确认 + 产物确认）→ MB（送达即可）→ MC（终点，空判据）。 */
  private final ProcessDefinition definition =
      new ProcessDefinition(
          "test-v1",
          List.of(
              new MilestoneDefinition(
                  "MA",
                  "判据混合",
                  "plan_image",
                  List.of(),
                  List.of(),
                  new CompletionCriteria(
                      List.of(
                          SlotCriterion.filled("city"),
                          SlotCriterion.userConfirmed("style_direction")),
                      List.of(ArtifactCriterion.confirmed("plan_image"))),
                  List.of(),
                  null),
              new MilestoneDefinition(
                  "MB",
                  "送达即可",
                  "render_image",
                  List.of(),
                  List.of(),
                  new CompletionCriteria(
                      List.of(), List.of(ArtifactCriterion.presented("render_image"))),
                  List.of(OnEnterAction.createTask("render_image")),
                  null),
              new MilestoneDefinition(
                  "MC",
                  "终点",
                  null,
                  List.of(),
                  List.of(),
                  CompletionCriteria.none(),
                  List.of(),
                  null)));

  @Test
  void transitionsWithOnEnterActionsWhenAllCriteriaSatisfied() {
    List<Slot> slots =
        List.of(
            slot("city", "杭州", CognitiveState.OBSERVED),
            slot("style_direction", "奶油", CognitiveState.USER_CONFIRMED));
    List<Artifact> artifacts = List.of(artifact("plan_image", ArtifactStatus.CONFIRMED));

    Optional<MilestoneTransition> transition =
        policy.checkCompletion(definition, "MA", slots, artifacts);

    assertTrue(transition.isPresent());
    assertEquals("MA", transition.get().fromMilestoneId());
    assertEquals("MB", transition.get().toMilestoneId());
    assertEquals(
        List.of(OnEnterAction.createTask("render_image")), transition.get().onEnterActions());
  }

  @Test
  void staysWhenNothingSatisfied() {
    assertTrue(policy.checkCompletion(definition, "MA", List.of(), List.of()).isEmpty());
  }

  @Test
  void staysWhenOnlyPartOfCriteriaSatisfied() {
    // 槽位全齐、产物仅 PRESENTED 未 CONFIRMED —— 部分满足不迁移
    List<Slot> slots =
        List.of(
            slot("city", "杭州", CognitiveState.OBSERVED),
            slot("style_direction", "奶油", CognitiveState.USER_CONFIRMED));
    List<Artifact> presentedOnly = List.of(artifact("plan_image", ArtifactStatus.PRESENTED));
    assertTrue(policy.checkCompletion(definition, "MA", slots, presentedOnly).isEmpty());

    // 产物达标、槽位认知状态未达 USER_CONFIRMED —— 同样不迁移
    List<Slot> proposedOnly =
        List.of(
            slot("city", "杭州", CognitiveState.OBSERVED),
            slot("style_direction", "奶油", CognitiveState.PROPOSED));
    List<Artifact> artifacts = List.of(artifact("plan_image", ArtifactStatus.CONFIRMED));
    assertTrue(policy.checkCompletion(definition, "MA", proposedOnly, artifacts).isEmpty());
  }

  @Test
  void presentedRequirementAcceptsConfirmedAsWell() {
    // 状态递进：要求 PRESENTED 时 CONFIRMED 亦达标
    Optional<MilestoneTransition> transition =
        policy.checkCompletion(
            definition,
            "MB",
            List.of(),
            List.of(artifact("render_image", ArtifactStatus.CONFIRMED)));
    assertTrue(transition.isPresent());
    assertEquals("MC", transition.get().toMilestoneId());
  }

  @Test
  void rejectedArtifactNeverSatisfies() {
    assertTrue(
        policy
            .checkCompletion(
                definition,
                "MB",
                List.of(),
                List.of(artifact("render_image", ArtifactStatus.REJECTED)))
            .isEmpty());
  }

  @Test
  void terminalMilestoneWithEmptyCriteriaNeverAutoCompletes() {
    assertTrue(policy.checkCompletion(definition, "MC", List.of(), List.of()).isEmpty());
  }

  private static Slot slot(String key, String value, CognitiveState state) {
    return new Slot(PROJECT_ID, key, value, state, "evt-1", 0.9, "MA");
  }

  private static Artifact artifact(String artifactType, ArtifactStatus status) {
    return new Artifact(
        "a-" + artifactType, PROJECT_ID, "MA", artifactType, 1, "oss://x", "{}", "{}", status);
  }
}
