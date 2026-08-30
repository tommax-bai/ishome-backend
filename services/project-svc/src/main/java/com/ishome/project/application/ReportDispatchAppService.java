package com.ishome.project.application;

import com.github.f4b6a3.ulid.UlidCreator;
import com.ishome.project.domain.port.ReportComposeGateway;
import com.ishome.project.domain.rulebook.ReportDataPackage;
import com.ishome.project.domain.rulebook.ReportDispatchReceipt;
import org.springframework.stereotype.Service;

/**
 * 两条线的接缝（图 v0.2 §2）：求值线出包 → 铸 report_id → 派给成文线。
 *
 * <p>顺序是有理由的：**先铸 id 再派发**。id 铸在派发之前，重试才有幂等键可握（裁决③）；铸在编排侧则重试的那一边 手上没有键，一次网络抖动就多出一份报告。
 *
 * <p>本服务**不落库、无事务**：project-svc 这一侧不存报告状态——存了就是第二台状态机（规则 8.1）。 report_id 的去向是回给调用方，由里程碑引擎按自己的时序记账。
 *
 * <p>派发失败**响亮抛出**（{@code ReportDispatchException}），不吞不降级：包是好的但报告没在生成， 这两件事必须让调用方分得清。
 */
@Service
public class ReportDispatchAppService {

  private final ReportEvaluationAppService reportEvaluationAppService;
  private final ReportComposeGateway reportComposeGateway;

  public ReportDispatchAppService(
      ReportEvaluationAppService reportEvaluationAppService,
      ReportComposeGateway reportComposeGateway) {
    this.reportEvaluationAppService = reportEvaluationAppService;
    this.reportComposeGateway = reportComposeGateway;
  }

  public ReportDispatchResult dispatch(ReportDispatchCommand command) {
    String reportId = newReportId();
    ReportDataPackage dataPackage =
        reportEvaluationAppService.evaluate(
            command.domains(),
            command.anonymousProfile(),
            command.entitlement(),
            command.lockedTextsByArtifact());
    ReportDispatchReceipt receipt =
        reportComposeGateway.dispatch(reportId, command.domains(), dataPackage);
    return new ReportDispatchResult(reportId, receipt);
  }

  /** 与本仓既有 id 同形（ULID，26 字符）：将来落表就是 varchar(26)，不用再换一套。 */
  private static String newReportId() {
    return UlidCreator.getUlid().toString();
  }
}
