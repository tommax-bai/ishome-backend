package com.ishome.project.domain.rulebook;

/**
 * 成文线派发失败：报告数据包没能交到编排侧手上（连不上、超时、非 2xx、回执解析不了）。
 *
 * <p>**响亮失败，不静默降级**——派发失败时求值线手里的包是有效的，但报告没在生成；吞掉异常返回一个 假回执，调用方会以为报告在路上（同图 v0.2 §3 "绝不静默假成功"的同一条纪律）。
 * 重试由调用方决定，重试必须带回同一个 {@code reportId}，幂等键在重试的那一侧（裁决③）。
 */
public class ReportDispatchException extends RuntimeException {

  private final String reportId;

  public ReportDispatchException(String reportId, String message, Throwable cause) {
    super(message, cause);
    this.reportId = reportId;
  }

  public ReportDispatchException(String reportId, String message) {
    this(reportId, message, null);
  }

  public String reportId() {
    return reportId;
  }
}
