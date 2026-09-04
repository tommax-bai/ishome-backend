package com.ishome.project;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ishome.project.application.ProcessDefinitionAppService;
import com.ishome.project.infrastructure.definition.ProcessDefinitionRepositoryImpl;
import com.ishome.project.interfaces.rest.ProcessDefinitionsController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 流程定义分发接口：v1 内置定义（M0-M6 数据化）分发与未知版本 404。 */
class ProcessDefinitionsEndpointTest {

  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(
              new ProcessDefinitionsController(
                  new ProcessDefinitionAppService(new ProcessDefinitionRepositoryImpl())))
          .build();

  @Test
  void servesBuiltinV1DefinitionWithAllMilestonesAsData() throws Exception {
    mockMvc
        .perform(get("/api/v1/process-definitions/v1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value("v1"))
        .andExpect(jsonPath("$.milestones.length()").value(8))
        .andExpect(jsonPath("$.milestones[0].id").value("M0"))
        .andExpect(jsonPath("$.milestones[7].id").value("M6"))
        // project 消费切片：判据（简单谓词）+ on_enter + 修订预算
        // M0 判据只剩两样：面积 + 户型图（用户裁决 2026-08-31）；M0.5 三张图都送达才算
        .andExpect(jsonPath("$.milestones[0].completion_criteria.slots.length()").value(2))
        .andExpect(jsonPath("$.milestones[1].completion_criteria.artifacts.length()").value(3))
        .andExpect(
            jsonPath("$.milestones[1].completion_criteria.artifacts[0].artifact_type")
                .value("vision_mood_image"))
        .andExpect(
            jsonPath("$.milestones[1].completion_criteria.artifacts[0].required_status")
                .value("PRESENTED"))
        .andExpect(jsonPath("$.milestones[1].on_enter[0].type").value("CREATE_TASK"))
        .andExpect(jsonPath("$.milestones[1].on_enter[0].params.task_type").value("vision_image"))
        .andExpect(jsonPath("$.milestones[3].revision.budget_rounds").value(3))
        // chat 消费切片：槽位 schema / 修订维度词表 / 动作白名单
        .andExpect(jsonPath("$.milestones[0].required_slots[0].key").value("floorplan"))
        .andExpect(
            jsonPath("$.milestones[2].completion_criteria.slots[0].accepted_states[0]")
                .value("USER_CONFIRMED"))
        .andExpect(jsonPath("$.milestones[3].revision.dimensions[0]").value("layout_density"))
        .andExpect(jsonPath("$.milestones[0].allowed_actions[0]").value("ask_to_collect"))
        // 终点里程碑：空判据，不自动完成
        .andExpect(jsonPath("$.milestones[7].completion_criteria.slots.length()").value(0))
        .andExpect(jsonPath("$.milestones[7].completion_criteria.artifacts.length()").value(0));
  }

  @Test
  void unknownVersionYields404() throws Exception {
    mockMvc.perform(get("/api/v1/process-definitions/v999")).andExpect(status().isNotFound());
  }
}
