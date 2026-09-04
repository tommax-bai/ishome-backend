package com.ishome.project.application;

import com.ishome.project.domain.GenerationFailure;
import java.util.List;

/**
 * 生成任务结果回流命令（contracts project.v1 {@code generation_task_result} 的应用层形态）。
 *
 * <p>{@code status} 只有 completed / failed 两值（词表归契约）；{@code products} 为编排侧词表的产物清单， 映射成本服务的
 * artifact_type 在 {@link VisualsProductCatalog}。生成侧不知用户是谁：命令里没有身份字段。
 */
public record GenerationTaskResultCommand(
    String taskId,
    String status,
    List<GenerationTaskProductCommand> products,
    GenerationFailure failure,
    String workflowId,
    String runId) {
  public static final String STATUS_COMPLETED = "completed";
  public static final String STATUS_FAILED = "failed";

  public boolean isCompleted() {
    return STATUS_COMPLETED.equals(status);
  }
}
