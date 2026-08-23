package com.ishome.project.application;

/**
 * 业务事实 feedback_received——chat 受限映射产出的结构化修订指令 {target, dimension, direction} （维度词表枚举校验在
 * chat；本服务只做词表内校验与修订预算判定）。
 */
public record FeedbackReceivedCommand(
    String projectId,
    String artifactId,
    String target,
    String dimension,
    String direction,
    String sourceEventId) {}
