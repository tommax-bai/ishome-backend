package com.ishome.project.application;

/** 业务事实 artifact_confirmed——chat 识别"确认"（确认闭环，当事人校验）后发来的结构化事实； 落库触发里程碑引擎 checkCompletion。 */
public record ArtifactConfirmedCommand(String projectId, String artifactId, String sourceEventId) {}
