package com.ishome.project;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ishome.project.application.ReportBookLinkAppService;
import com.ishome.project.application.ReportDispatchAppService;
import com.ishome.project.application.ReportEvaluationAppService;
import com.ishome.project.domain.port.ReportBookStore;
import com.ishome.project.domain.rulebook.ParameterAsset;
import com.ishome.project.domain.rulebook.ReleaseSnapshot;
import com.ishome.project.domain.rulebook.ReportBookLink;
import com.ishome.project.domain.rulebook.ReportDispatchException;
import com.ishome.project.interfaces.rest.ReportsController;
import com.ishome.project.testsupport.InMemoryReleaseRepository;
import com.ishome.project.testsupport.RecordingReportComposeGateway;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 报告触发口：202＝包交出去了（不是"报告好了"），以及两条失败码各自的含义。 */
class ReportsEndpointTest {

  private static final String REQUEST_BODY =
      """
      {
        "domains": ["ergonomics"],
        "entitlement": "PAID",
        "anonymousProfile": {
          "chiefHeightMm": 1700,
          "tallestHeightMm": 1780,
          "eyeHeightMm": 1600,
          "tvScreenHeightMm": 600,
          "layoutFeatures": {},
          "cityTier": "一线"
        },
        "lockedTextsByArtifact": {"ergonomics": ["DISCLAIM_APPENDIX"]}
      }
      """;

  private static final ReleaseSnapshot ERGONOMICS =
      new ReleaseSnapshot(
          "ergonomics",
          "ergonomics@v8",
          List.of(
              new ParameterAsset(
                  "lkp-socket-height",
                  "插座中心高",
                  "locating",
                  "single",
                  300,
                  null,
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

  /** 桩件册存储：出册了就给一条链接，没出册就是空——不区分"生成失败"与"还没生成"（那是里程碑的账）。 */
  private static ReportBookStore bookStore(boolean rendered) {
    return (reportId, validity) ->
        rendered
            ? Optional.of(
                new ReportBookLink(
                    URI.create("https://ishome.oss-cn-beijing.aliyuncs.com/reports/" + reportId),
                    Instant.parse("2026-09-06T12:00:00Z")))
            : Optional.empty();
  }

  private static MockMvc mockMvcWith(RecordingReportComposeGateway gateway) {
    return mockMvcWith(gateway, bookStore(true));
  }

  private static MockMvc mockMvcWith(RecordingReportComposeGateway gateway, ReportBookStore store) {
    return MockMvcBuilders.standaloneSetup(
            new ReportsController(
                new ReportDispatchAppService(
                    new ReportEvaluationAppService(
                        new InMemoryReleaseRepository().with(ERGONOMICS)),
                    gateway),
                new ReportBookLinkAppService(store, Duration.ofDays(7))))
        .build();
  }

  private static org.springframework.test.web.servlet.ResultActions dispatch(MockMvc mockMvc)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/reports").contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY));
  }

  /** 202 + 回执：报告已交给成文线。**没有"报告成没成"字段**——那是里程碑的账，不是这一跳的。 */
  @Test
  void acceptsDispatchAndAnswersWithAddressableReceipt() throws Exception {
    dispatch(mockMvcWith(new RecordingReportComposeGateway()))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.reportId").isNotEmpty())
        .andExpect(jsonPath("$.workflowId").isNotEmpty())
        .andExpect(jsonPath("$.runId").isNotEmpty());
  }

  /** 域没发布过 release → 422：改数据不改请求。 */
  @Test
  void unpublishedDomainYields422() throws Exception {
    mockMvcWith(new RecordingReportComposeGateway())
        .perform(
            post("/api/v1/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_BODY.replace("\"ergonomics\"]", "\"lighting\"]")))
        .andExpect(status().isUnprocessableEntity());
  }

  /** 出册了 → 200 + 一条带失效时间的链接。业主点这条就能读到自己的报告。 */
  @Test
  void issuesAnOpenableLinkOnceTheBookIsRendered() throws Exception {
    mockMvcWith(new RecordingReportComposeGateway())
        .perform(get("/api/v1/reports/{id}/link", "01M18E1YGKVQZGCCNB0PCY4K7B"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").isNotEmpty())
        .andExpect(jsonPath("$.expiresAt").isNotEmpty());
  }

  /** 还没出册 → 404。**这不是"没这份报告"**：报告是异步生成的，问早了本来就该是"还没有"。 */
  @Test
  void notYetRenderedYields404() throws Exception {
    mockMvcWith(new RecordingReportComposeGateway(), bookStore(false))
        .perform(get("/api/v1/reports/{id}/link", "01M18E1YGKVQZGCCNB0PCY4K7B"))
        .andExpect(status().isNotFound());
  }

  /** 编排侧没接住 → 502：责任在下游那一跳，不让调用方以为自己传错了。 */
  @Test
  void dispatchFailureYields502() throws Exception {
    dispatch(
            mockMvcWith(
                new RecordingReportComposeGateway()
                    .failingWith(new ReportDispatchException("rpt", "连不上编排侧"))))
        .andExpect(status().isBadGateway());
  }
}
