package com.ishome.project;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.ishome.project.interfaces.rest.GenerationTasksController;
import com.ishome.project.interfaces.rest.ProjectsController;
import com.ishome.project.testsupport.WiringFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** project.v1 三个入口的线上形态（snake_case 报文、状态码、错误体）——契约在 contracts，这里断言两侧对得上。 */
class WiringEndpointsTest {
  private static final String OWNER_JSON =
      """
      {"owner":{"channel_type":"mock","channel_instance":"mock:local","external_user_id":"ou_x"}}
      """;

  private WiringFixture fixture;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    fixture = new WiringFixture();
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new ProjectsController(fixture.projectAppService),
                new GenerationTasksController(fixture.projectAppService))
            .build();
  }

  @Test
  void findOrCreateThenSlotsThenResultRoundTrip() throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/projects")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OWNER_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.created").value(true))
            .andExpect(jsonPath("$.current_milestone").value("M0"))
            .andReturn();
    String projectId =
        fixture
            .objectMapper
            .readTree(created.getResponse().getContentAsString())
            .get("project_id")
            .asText();

    mockMvc
        .perform(
            post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON).content(OWNER_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(false))
        .andExpect(jsonPath("$.project_id").value(projectId));

    MvcResult filled =
        mockMvc
            .perform(
                post("/api/v1/projects/" + projectId + "/slots")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"slots":[
                          {"slot_key":"floorplan","value":"uploads/%s/original.png","cognitive_state":"observed","source_event_id":"m1"},
                          {"slot_key":"building_area_sqm","value":"138","cognitive_state":"observed","source_event_id":"m2","confidence":0.9},
                          {"slot_key":"floor_area_ratio_percent","value":"80","cognitive_state":"inferred"}
                        ]}
                        """
                            .formatted("e".repeat(64))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.advanced").value(true))
            .andExpect(jsonPath("$.current_milestone").value("M0.5"))
            .andExpect(jsonPath("$.created_task_ids.length()").value(1))
            .andReturn();
    JsonNode progress = fixture.objectMapper.readTree(filled.getResponse().getContentAsString());
    String taskId = progress.get("created_task_ids").get(0).asText();

    mockMvc
        .perform(
            post("/api/v1/generation-tasks/" + taskId + "/result")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"task_id":"%s","status":"completed","workflow_id":"wf","run_id":"r","products":[
                      {"product":"mood_image","object_key":"uploads/e/atmosphere-cream-journal-captioned.png","content_type":"image/png","gen_params":{"template_id":"cream-journal"}},
                      {"product":"brief_image","object_key":"uploads/e/plan-brief.png"},
                      {"product":"style_image","object_key":"uploads/e/atmosphere-lifestyle-notebook-handwritten.jpg","content_type":"image/jpeg"}
                    ]}
                    """
                        .formatted(taskId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accepted").value(true))
        .andExpect(jsonPath("$.duplicate").value(false))
        .andExpect(jsonPath("$.registered_artifact_ids.length()").value(3));

    mockMvc
        .perform(
            post("/api/v1/generation-tasks/" + taskId + "/result")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"completed\",\"products\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.duplicate").value(true));
  }

  @Test
  void unknownProjectAndTaskAre404WithDetail() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/projects/01UNKNOWN/slots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"slots\":[{\"slot_key\":\"floorplan\",\"value\":\"k\",\"cognitive_state\":\"observed\"}]}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").exists());
    mockMvc
        .perform(
            post("/api/v1/generation-tasks/01UNKNOWN/result")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"status\":\"failed\",\"products\":[],\"failure\":{\"code\":\"x\",\"detail\":\"y\"}}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").exists());
  }

  @Test
  void badCognitiveStateOrStatusIs400() throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/projects")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(OWNER_JSON))
            .andReturn();
    String projectId =
        fixture
            .objectMapper
            .readTree(created.getResponse().getContentAsString())
            .get("project_id")
            .asText();
    mockMvc
        .perform(
            post("/api/v1/projects/" + projectId + "/slots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"slots\":[{\"slot_key\":\"floorplan\",\"value\":\"k\",\"cognitive_state\":\"confirmed\"}]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").exists());
    mockMvc
        .perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }
}
