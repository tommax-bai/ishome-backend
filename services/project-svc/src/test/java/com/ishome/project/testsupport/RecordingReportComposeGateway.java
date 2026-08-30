package com.ishome.project.testsupport;

import com.ishome.project.domain.port.ReportComposeGateway;
import com.ishome.project.domain.rulebook.ReportDataPackage;
import com.ishome.project.domain.rulebook.ReportDispatchReceipt;
import java.util.ArrayList;
import java.util.List;

/** 成文线派发 port 的假实现：记下每一次派发，供断言"铸了什么 id、派了哪份包"。 */
public class RecordingReportComposeGateway implements ReportComposeGateway {

  /** 一次派发的留痕。 */
  public record Dispatched(String reportId, List<String> domains, ReportDataPackage dataPackage) {}

  private final List<Dispatched> dispatches = new ArrayList<>();
  private RuntimeException failure;

  public RecordingReportComposeGateway failingWith(RuntimeException failure) {
    this.failure = failure;
    return this;
  }

  public List<Dispatched> dispatches() {
    return List.copyOf(dispatches);
  }

  @Override
  public ReportDispatchReceipt dispatch(
      String reportId, List<String> domains, ReportDataPackage dataPackage) {
    dispatches.add(new Dispatched(reportId, domains, dataPackage));
    if (failure != null) {
      throw failure;
    }
    return new ReportDispatchReceipt(reportId, "report-compose-" + reportId, "run-" + reportId);
  }
}
