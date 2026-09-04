package com.ishome.project.interfaces.rest;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ishome.project.application.GenerationTaskProductCommand;
import com.ishome.project.application.GenerationTaskResultCommand;
import com.ishome.project.domain.GenerationFailure;
import java.util.List;
import java.util.Map;

/** contracts project.v1 {@code generation_task_result}（snake_case）。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GenerationTaskResultRequest(
    String taskId,
    String status,
    List<GenerationTaskProductRequest> products,
    FailureRequest failure,
    String workflowId,
    String runId) {

  GenerationTaskResultCommand toCommand(String pathTaskId) {
    if (taskId != null && !taskId.isBlank() && !taskId.equals(pathTaskId)) {
      throw new IllegalArgumentException("路径与报文里的 task_id 不一致：" + pathTaskId + " ≠ " + taskId);
    }
    if (!GenerationTaskResultCommand.STATUS_COMPLETED.equals(status)
        && !GenerationTaskResultCommand.STATUS_FAILED.equals(status)) {
      throw new IllegalArgumentException("status 只能是 completed / failed：" + status);
    }
    List<GenerationTaskProductCommand> productCommands =
        products == null
            ? List.of()
            : products.stream().map(GenerationTaskProductRequest::toCommand).toList();
    if (GenerationTaskResultCommand.STATUS_FAILED.equals(status) && failure == null) {
      throw new IllegalArgumentException("failed 必须带 failure");
    }
    return new GenerationTaskResultCommand(
        pathTaskId,
        status,
        productCommands,
        failure == null ? null : new GenerationFailure(failure.code(), failure.detail()),
        workflowId,
        runId);
  }

  /** contracts project.v1 {@code generation_task_product}。 */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record GenerationTaskProductRequest(
      String product, String objectKey, String contentType, Map<String, Object> genParams) {
    GenerationTaskProductCommand toCommand() {
      if (product == null || product.isBlank() || objectKey == null || objectKey.isBlank()) {
        throw new IllegalArgumentException("产物缺 product 或 object_key");
      }
      return new GenerationTaskProductCommand(
          product, objectKey, contentType, genParams == null ? Map.of() : genParams);
    }
  }

  /** {@code failure} 子结构。 */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record FailureRequest(String code, String detail) {}
}
