package com.ishome.project.domain;

/**
 * 用户决策记录（UserDecisions，对齐文档 §5.1 svc_project.decisions）：确认/否决/里程碑进入，
 * 含来源事件引用（sourceEventId，幂等与审计锚点）。
 */
public record Decision(
    String id,
    String projectId,
    DecisionType decisionType,
    String milestone,
    String artifactId,
    String sourceEventId) {}
