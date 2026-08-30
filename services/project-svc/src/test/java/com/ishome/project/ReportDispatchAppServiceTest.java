package com.ishome.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ishome.project.application.ReportDispatchAppService;
import com.ishome.project.application.ReportDispatchCommand;
import com.ishome.project.application.ReportDispatchResult;
import com.ishome.project.application.ReportEvaluationAppService;
import com.ishome.project.domain.rulebook.ArtifactEntitlement;
import com.ishome.project.domain.rulebook.EvaluationInput;
import com.ishome.project.domain.rulebook.ParameterAsset;
import com.ishome.project.domain.rulebook.ReleaseNotFoundException;
import com.ishome.project.domain.rulebook.ReleaseSnapshot;
import com.ishome.project.domain.rulebook.ReportDispatchException;
import com.ishome.project.testsupport.InMemoryReleaseRepository;
import com.ishome.project.testsupport.RecordingReportComposeGateway;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 两条线的接缝（图 v0.2 §2）：铸 report_id → 求值 → 派发，以及失败时不静默假成功。 */
class ReportDispatchAppServiceTest {

  private static final EvaluationInput INPUT =
      new EvaluationInput(1700, 1780, 1600, 600, Map.of(), "一线");

  private static final ReleaseSnapshot ERGONOMICS =
      new ReleaseSnapshot(
          "ergonomics",
          "ergonomics@v8",
          List.of(
              new ParameterAsset(
                  "lkp-socket-height",
                  "插座中心高",
                  "locating",
                  Map.of("v", 300),
                  null,
                  "mm",
                  "draft",
                  "测试源",
                  1)),
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          List.of());

  private final RecordingReportComposeGateway gateway = new RecordingReportComposeGateway();

  private ReportDispatchAppService serviceWith(RecordingReportComposeGateway composeGateway) {
    return new ReportDispatchAppService(
        new ReportEvaluationAppService(new InMemoryReleaseRepository().with(ERGONOMICS)),
        composeGateway);
  }

  private static ReportDispatchCommand command() {
    return new ReportDispatchCommand(
        List.of("ergonomics"),
        INPUT,
        ArtifactEntitlement.PAID,
        Map.of("ergonomics", List.of("DISCLAIM_APPENDIX")));
  }

  /** 铸造在派发之前：派出去的那份包与回给调用方的 id 是同一个，重试才有幂等键可握（裁决③）。 */
  @Test
  void mintsReportIdBeforeDispatchAndHandsBackTheSameOne() {
    ReportDispatchResult result = serviceWith(gateway).dispatch(command());

    assertEquals(1, gateway.dispatches().size());
    assertEquals(result.reportId(), gateway.dispatches().get(0).reportId());
    assertEquals(result.reportId(), result.receipt().reportId());
    // ULID 26 字符：将来落表就是 varchar(26)，与本仓既有 id 同形
    assertEquals(26, result.reportId().length());
  }

  /** 每次派发铸一个新 id：同一份输入派两次是两份报告，不是同一份的重试。 */
  @Test
  void mintsDistinctIdPerDispatch() {
    ReportDispatchAppService service = serviceWith(gateway);

    assertNotEquals(service.dispatch(command()).reportId(), service.dispatch(command()).reportId());
  }

  /** 必挂集并集随包下发：调用方按 art- 传入的那半与求值线派生的那半都在派出去的包里。 */
  @Test
  void dispatchesPackageCarryingUnionedLockedTexts() {
    serviceWith(gateway).dispatch(command());

    assertEquals(
        List.of("DISCLAIM_APPENDIX", "GUIDE_SITE_CHECK"),
        gateway.dispatches().get(0).dataPackage().lockedTextsByDomain().get("ergonomics"));
  }

  /** 派发失败响亮抛出，不吞不降级：包是好的但报告没在生成，这两件事必须让调用方分得清。 */
  @Test
  void surfacesDispatchFailureInsteadOfFakingSuccess() {
    ReportDispatchAppService service =
        serviceWith(
            new RecordingReportComposeGateway()
                .failingWith(new ReportDispatchException("rpt", "连不上编排侧")));

    assertThrows(ReportDispatchException.class, () -> service.dispatch(command()));
  }

  /** 域没有 release 快照 → 求值阶段就失败，一个字都不往编排侧发。 */
  @Test
  void failsBeforeDispatchWhenDomainHasNoRelease() {
    ReportDispatchAppService service = serviceWith(gateway);
    ReportDispatchCommand unpublished =
        new ReportDispatchCommand(List.of("lighting"), INPUT, ArtifactEntitlement.PAID, Map.of());

    assertThrows(ReleaseNotFoundException.class, () -> service.dispatch(unpublished));
    assertTrue(gateway.dispatches().isEmpty());
  }
}
