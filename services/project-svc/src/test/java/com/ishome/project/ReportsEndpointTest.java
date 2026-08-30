package com.ishome.project;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ishome.project.application.ReportDispatchAppService;
import com.ishome.project.application.ReportEvaluationAppService;
import com.ishome.project.domain.rulebook.ParameterAsset;
import com.ishome.project.domain.rulebook.ReleaseSnapshot;
import com.ishome.project.domain.rulebook.ReportDispatchException;
import com.ishome.project.interfaces.rest.ReportsController;
import com.ishome.project.testsupport.InMemoryReleaseRepository;
import com.ishome.project.testsupport.RecordingReportComposeGateway;
import java.util.List;
import java.util.Map;
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

  private static MockMvc mockMvcWith(RecordingReportComposeGateway gateway) {
    return MockMvcBuilders.standaloneSetup(
            new ReportsController(
                new ReportDispatchAppService(
                    new ReportEvaluationAppService(
                        new InMemoryReleaseRepository().with(ERGONOMICS)),
                    gateway)))
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
