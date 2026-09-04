package com.ishome.project.interfaces.rest;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ishome.project.application.SlotFilledCommand;
import com.ishome.project.domain.CognitiveState;
import java.util.Locale;

/** contracts project.v1 {@code slot_fill}：cognitive_state 为六值小写词表，confidence 缺省 1。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SlotFillRequest(
    String slotKey, String value, String cognitiveState, String sourceEventId, Double confidence) {
  SlotFilledCommand toCommand(String projectId) {
    if (slotKey == null || slotKey.isBlank()) {
      throw new IllegalArgumentException("slot_key 为空");
    }
    if (value == null) {
      throw new IllegalArgumentException("槽位 " + slotKey + " 没有 value");
    }
    if (cognitiveState == null || cognitiveState.isBlank()) {
      throw new IllegalArgumentException("槽位 " + slotKey + " 没有 cognitive_state");
    }
    CognitiveState state;
    try {
      state = CognitiveState.valueOf(cognitiveState.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("cognitive_state 不在六值词表内：" + cognitiveState, e);
    }
    return new SlotFilledCommand(
        projectId, slotKey, value, state, sourceEventId, confidence == null ? 1.0 : confidence);
  }
}
