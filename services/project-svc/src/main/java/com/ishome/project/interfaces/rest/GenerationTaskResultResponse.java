package com.ishome.project.interfaces.rest;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ishome.project.application.GenerationTaskResultReceipt;
import java.util.List;

/** contracts project.v1 {@code generation_task_result_receipt}。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GenerationTaskResultResponse(
    String taskId, boolean accepted, boolean duplicate, List<String> registeredArtifactIds) {
  static GenerationTaskResultResponse from(GenerationTaskResultReceipt receipt) {
    return new GenerationTaskResultResponse(
        receipt.taskId(), receipt.accepted(), receipt.duplicate(), receipt.registeredArtifactIds());
  }
}
