package com.ishome.project.interfaces.rest;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ishome.project.domain.CognitiveState;
import com.ishome.project.domain.definition.MilestoneDefinition;
import com.ishome.project.domain.definition.OnEnterAction;
import com.ishome.project.domain.definition.ProcessDefinition;
import com.ishome.project.domain.definition.RevisionRule;
import com.ishome.project.domain.definition.SlotDefinition;
import java.util.List;
import java.util.Map;

/** 流程定义分发出参（JSON snake_case，与配置数据口径一致；枚举输出 UPPER_SNAKE 字符串）。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProcessDefinitionResponse(String version, List<MilestonePayload> milestones) {

  public static ProcessDefinitionResponse from(ProcessDefinition definition) {
    return new ProcessDefinitionResponse(
        definition.version(),
        definition.milestones().stream().map(MilestonePayload::from).toList());
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record MilestonePayload(
      String id,
      String name,
      String producedArtifactType,
      List<SlotPayload> requiredSlots,
      List<String> allowedActions,
      CriteriaPayload completionCriteria,
      List<ActionPayload> onEnter,
      RevisionPayload revision) {

    static MilestonePayload from(MilestoneDefinition milestone) {
      return new MilestonePayload(
          milestone.id(),
          milestone.name(),
          milestone.producedArtifactType(),
          milestone.requiredSlots().stream().map(SlotPayload::from).toList(),
          milestone.allowedActions(),
          CriteriaPayload.from(milestone),
          milestone.onEnterActions().stream().map(ActionPayload::from).toList(),
          RevisionPayload.from(milestone.revisionRule()));
    }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record SlotPayload(
      String key, String valueType, String extractionHint, List<String> options) {

    static SlotPayload from(SlotDefinition slot) {
      return new SlotPayload(slot.key(), slot.valueType(), slot.extractionHint(), slot.options());
    }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record CriteriaPayload(
      List<SlotCriterionPayload> slots, List<ArtifactCriterionPayload> artifacts) {

    static CriteriaPayload from(MilestoneDefinition milestone) {
      return new CriteriaPayload(
          milestone.completionCriteria().slotCriteria().stream()
              .map(
                  criterion ->
                      new SlotCriterionPayload(
                          criterion.slotKey(),
                          criterion.acceptedStates().stream()
                              .map(CognitiveState::name)
                              .sorted()
                              .toList()))
              .toList(),
          milestone.completionCriteria().artifactCriteria().stream()
              .map(
                  criterion ->
                      new ArtifactCriterionPayload(
                          criterion.artifactType(), criterion.requiredStatus().name()))
              .toList());
    }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record SlotCriterionPayload(String slotKey, List<String> acceptedStates) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record ArtifactCriterionPayload(String artifactType, String requiredStatus) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record ActionPayload(String type, Map<String, String> params) {

    static ActionPayload from(OnEnterAction action) {
      return new ActionPayload(action.type().name(), action.params());
    }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record RevisionPayload(List<String> dimensions, int budgetRounds) {

    static RevisionPayload from(RevisionRule rule) {
      return rule == null ? null : new RevisionPayload(rule.dimensions(), rule.budgetRounds());
    }
  }
}
