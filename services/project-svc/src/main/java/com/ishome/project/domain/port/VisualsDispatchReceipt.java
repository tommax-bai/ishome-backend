package com.ishome.project.domain.port;

/** 派发回执：编排侧定址信息，不是状态（同 {@link com.ishome.project.domain.rulebook.ReportDispatchReceipt}）。 */
public record VisualsDispatchReceipt(String taskId, String workflowId, String runId) {}
