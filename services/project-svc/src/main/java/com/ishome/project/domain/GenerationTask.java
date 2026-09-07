package com.ishome.project.domain;

/**
 * 生成任务业务真相（对齐文档 §5.1 svc_project.generation_tasks）。
 *
 * <p>执行、重试、心跳、超时语义全部在 Temporal（任务层），此处只记业务事实； {@code inputSnapshot} 为任务入参快照（JSON 字符串），{@code
 * artifactId} 完成后回填（多件产物时为首件）， {@code result} 为编排侧回流的结论原文（JSON 字符串，审计用；未回流为 null）， {@code
 * triggerEventId} 为触发本任务的渠道事件 id（slot_filled 的 sourceEventId；不由渠道消息直接触发时为 null）。
 *
 * <p>{@code triggerEventId} 是"同一条渠道消息不铸第二个任务"的判据载体（补派路径，用户裁决 2026-09-07）。
 */
public record GenerationTask(
    String id,
    String projectId,
    String taskType,
    String inputSnapshot,
    GenerationTaskStatus status,
    String artifactId,
    String result,
    String triggerEventId) {

  /** 未回流形态的构造（不由渠道消息直接触发）。 */
  public GenerationTask(
      String id,
      String projectId,
      String taskType,
      String inputSnapshot,
      GenerationTaskStatus status,
      String artifactId) {
    this(id, projectId, taskType, inputSnapshot, status, artifactId, null, null);
  }

  public GenerationTask withStatus(GenerationTaskStatus newStatus) {
    return new GenerationTask(
        id, projectId, taskType, inputSnapshot, newStatus, artifactId, result, triggerEventId);
  }

  public GenerationTask withResult(
      GenerationTaskStatus newStatus, String firstArtifactId, String resultJson) {
    return new GenerationTask(
        id,
        projectId,
        taskType,
        inputSnapshot,
        newStatus,
        firstArtifactId,
        resultJson,
        triggerEventId);
  }

  public boolean isSettled() {
    return status == GenerationTaskStatus.COMPLETED || status == GenerationTaskStatus.FAILED;
  }
}
