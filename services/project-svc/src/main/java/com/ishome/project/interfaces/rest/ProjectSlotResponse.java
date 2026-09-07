package com.ishome.project.interfaces.rest;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ishome.project.domain.Slot;
import java.util.Locale;

/**
 * contracts project.v1 {@code project_slot}：槽位真相的读形态。
 *
 * <p>cognitive_state 出去时转小写——枚举值在 Java 与 DB 里是 UPPER_SNAKE，契约上的词表是小写六值（与写入面 {@code slot_fill}
 * 同一份词表，方向相反）。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProjectSlotResponse(
    String slotKey, String value, String cognitiveState, String sourceEventId, double confidence) {
  static ProjectSlotResponse from(Slot slot) {
    return new ProjectSlotResponse(
        slot.slotKey(),
        slot.value(),
        slot.cognitiveState().name().toLowerCase(Locale.ROOT),
        slot.sourceEventId(),
        slot.confidence());
  }
}
