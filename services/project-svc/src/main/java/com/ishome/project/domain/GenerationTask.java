package com.ishome.project.domain;

/**
 * 生成任务业务真相（对齐文档 §5.1 svc_project.generation_tasks）。
 *
 * <p>执行、重试、心跳、超时语义全部在 Temporal（任务层），此处只记业务事实； {@code inputSnapshot} 为任务入参快照（JSON 字符串），{@code
 * artifactId} 完成后回填。
 */
public record GenerationTask(
    String id,
    String projectId,
    String taskType,
    String inputSnapshot,
    GenerationTaskStatus status,
    String artifactId) {}
