package com.ishome.project.domain;

/**
 * outbox 事件（对齐 2.6 纪律）：业务写与事件写同一事务落表，中继投递后回填 published_at。
 *
 * <p>{@code eventType} 为 CloudEvents type（contracts events/registry.md，省略 {@code com.ishome.} 前缀）；
 * {@code payload} 为 JSON 字符串。总线（RocketMQ）接入前，中继直接调会话侧 rpc；接入后中继改为发总线—— 换的是中继实现，事件不换名。
 */
public record OutboxEvent(
    String id, String aggregateType, String aggregateId, String eventType, String payload) {

  public static final String TYPE_DELIVERABLES_READY = "project.deliverables.ready";
  public static final String TYPE_GENERATION_TASK_FAILED = "project.generation-task.failed";
  public static final String AGGREGATE_PROJECT = "project";
}
