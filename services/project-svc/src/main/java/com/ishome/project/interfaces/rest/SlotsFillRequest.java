package com.ishome.project.interfaces.rest;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ishome.project.application.SlotFilledCommand;
import java.util.List;

/** contracts project.v1 {@code slots_fill_request}。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SlotsFillRequest(List<SlotFillRequest> slots) {
  List<SlotFilledCommand> toCommands(String projectId) {
    if (slots == null || slots.isEmpty()) {
      throw new IllegalArgumentException("slots 为空：没有事实可报");
    }
    return slots.stream().map(slot -> slot.toCommand(projectId)).toList();
  }
}
