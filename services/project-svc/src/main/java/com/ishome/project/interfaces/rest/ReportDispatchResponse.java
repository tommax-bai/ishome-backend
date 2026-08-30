package com.ishome.project.interfaces.rest;

import com.ishome.project.application.ReportDispatchResult;

/**
 * 报告派发响应：铸出的 report_id 与编排侧定址。
 *
 * <p>{@code workflowId}/{@code runId} 可能为空串——重试撞上"已在飞"时编排侧只答冲突不回定址（见 {@code
 * ReportDispatchReceipt}）。**空定址不代表派发失败**：失败一律是非 2xx。
 */
public record ReportDispatchResponse(String reportId, String workflowId, String runId) {

  static ReportDispatchResponse from(ReportDispatchResult result) {
    return new ReportDispatchResponse(
        result.reportId(), result.receipt().workflowId(), result.receipt().runId());
  }
}
