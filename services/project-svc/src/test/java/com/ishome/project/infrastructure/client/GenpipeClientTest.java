package com.ishome.project.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ishome.project.domain.rulebook.ArtifactEntitlement;
import com.ishome.project.domain.rulebook.EvaluationInput;
import com.ishome.project.domain.rulebook.ReportDataPackage;
import com.ishome.project.domain.rulebook.ReportDispatchException;
import com.ishome.project.domain.rulebook.ReportDispatchReceipt;
import com.ishome.project.testsupport.StubGenpipeServer;
import com.ishome.project.testsupport.StubGenpipeServer.Reply;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** 出站派发这一跳（裁决④）：线上字段名、重试射程、失败不静默。 */
class GenpipeClientTest {

  private static final ReportDataPackage PACKAGE =
      new ReportDataPackage(
          LocalDate.of(2026, 8, 29),
          ArtifactEntitlement.PAID,
          List.of("ergonomics"),
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          Map.of(),
          Map.of(),
          Map.of(),
          Map.of(),
          Map.of(),
          Map.of("ergonomics", List.of("GUIDE_SITE_CHECK")),
          new EvaluationInput(1700, 1780, 1600, 600, Map.of(), "一线", null, null));

  private static GenpipeClient clientFor(StubGenpipeServer server) {
    return new GenpipeClient(RestClient.builder(), server.baseUrl(), 2000, 5000);
  }

  private static ReportDispatchReceipt dispatch(GenpipeClient client) {
    return client.dispatch("01J0REPORTID0000000000000A", List.of("ergonomics"), PACKAGE);
  }

  /**
   * 线上字段名：外层 snake_case（编排侧 pydantic 模型），包体 camelCase（contracts schema）， {@code evaluatedOn} 是 ISO
   * 日期串不是时间戳数组。两套命名同报文共存是实测事实，这条断言就是它的锚。
   */
  @Test
  void sendsSnakeCaseEnvelopeAroundCamelCasePackage() {
    try (StubGenpipeServer server =
        new StubGenpipeServer()
            .replying(new Reply(202, "{\"workflow_id\":\"report-compose-x\",\"run_id\":\"r1\"}"))) {
      dispatch(clientFor(server));

      String body = server.requestBodies().get(0);
      assertTrue(body.contains("\"report_id\":\"01J0REPORTID0000000000000A\""), body);
      assertTrue(body.contains("\"package\":"), body);
      assertTrue(body.contains("\"evaluatedOn\":\"2026-08-29\""), body);
      assertTrue(body.contains("\"lockedTextsByDomain\""), body);
      assertTrue(body.contains("\"anonymousProfile\""), body);
      // max_rewrites / queues 不传：前者是设计定数不许私改，后者生产调用方一律不覆写
      assertTrue(!body.contains("max_rewrites"), body);
      assertTrue(!body.contains("queues"), body);
    }
  }

  /** 2xx 回执原样带回，reportId 由本侧填（编排侧不回它，回了也该以本侧铸的为准）。 */
  @Test
  void returnsReceiptFromOrchestrator() {
    try (StubGenpipeServer server =
        new StubGenpipeServer()
            .replying(
                new Reply(202, "{\"workflow_id\":\"report-compose-x\",\"run_id\":\"run-1\"}"))) {
      ReportDispatchReceipt receipt = dispatch(clientFor(server));

      assertEquals("01J0REPORTID0000000000000A", receipt.reportId());
      assertEquals("report-compose-x", receipt.workflowId());
      assertEquals("run-1", receipt.runId());
    }
  }

  /** 5xx 是瞬时故障：用同一个 report_id 同一份 body 重试一次（幂等键握在这一侧，裁决③）。 */
  @Test
  void retriesOnceOnServerErrorWithTheSameReportId() {
    try (StubGenpipeServer server =
        new StubGenpipeServer()
            .replying(
                new Reply(503, "{\"detail\":\"暂时不可用\"}"),
                new Reply(202, "{\"workflow_id\":\"w\",\"run_id\":\"r\"}"))) {
      ReportDispatchReceipt receipt = dispatch(clientFor(server));

      assertEquals(2, server.requestBodies().size());
      assertEquals(server.requestBodies().get(0), server.requestBodies().get(1));
      assertEquals("w", receipt.workflowId());
    }
  }

  /** 重试撞 409＝上一跳其实已经打进去了，按成功收；编排侧不随冲突回定址，故回执留空。 */
  @Test
  void treatsConflictOnRetryAsAlreadyDispatched() {
    try (StubGenpipeServer server =
        new StubGenpipeServer()
            .replying(new Reply(500, "{}"), new Reply(409, "{\"detail\":\"report 已启动\"}"))) {
      ReportDispatchReceipt receipt = dispatch(clientFor(server));

      assertEquals("01J0REPORTID0000000000000A", receipt.reportId());
      assertEquals("", receipt.workflowId());
      assertEquals("", receipt.runId());
    }
  }

  /** 首发就撞 409：这个 id 从没派过却已在飞，说明 id 撞了或有第二个派发方——响亮失败，不当成功。 */
  @Test
  void failsLoudWhenFirstAttemptConflicts() {
    try (StubGenpipeServer server =
        new StubGenpipeServer().replying(new Reply(409, "{\"detail\":\"report 已启动\"}"))) {
      GenpipeClient client = clientFor(server);

      assertThrows(ReportDispatchException.class, () -> dispatch(client));
    }
  }

  /** 重试用尽仍失败 → 抛，不返回假回执：包是好的但报告没在生成，两件事必须分得清。 */
  @Test
  void failsAfterRetriesAreExhausted() {
    try (StubGenpipeServer server =
        new StubGenpipeServer().replying(new Reply(503, "{}"), new Reply(503, "{}"))) {
      GenpipeClient client = clientFor(server);

      ReportDispatchException failure =
          assertThrows(ReportDispatchException.class, () -> dispatch(client));
      assertEquals("01J0REPORTID0000000000000A", failure.reportId());
      assertEquals(2, server.requestBodies().size());
    }
  }

  /** 连不上也是瞬时故障，同样重试后响亮失败——不把"下游没起来"读成"报告发出去了"。 */
  @Test
  void failsLoudWhenOrchestratorIsUnreachable() {
    GenpipeClient client = new GenpipeClient(RestClient.builder(), "http://127.0.0.1:1", 300, 300);

    assertThrows(ReportDispatchException.class, () -> dispatch(client));
  }
}
