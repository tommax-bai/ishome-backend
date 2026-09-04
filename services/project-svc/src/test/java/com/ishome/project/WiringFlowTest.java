package com.ishome.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.ishome.project.application.GenerationTaskProductCommand;
import com.ishome.project.application.GenerationTaskResultCommand;
import com.ishome.project.application.GenerationTaskResultReceipt;
import com.ishome.project.application.MilestoneProgressResult;
import com.ishome.project.application.ProjectFindOrCreateResult;
import com.ishome.project.application.SlotFilledCommand;
import com.ishome.project.domain.Artifact;
import com.ishome.project.domain.ArtifactStatus;
import com.ishome.project.domain.CognitiveState;
import com.ishome.project.domain.GenerationFailure;
import com.ishome.project.domain.GenerationTask;
import com.ishome.project.domain.GenerationTaskStatus;
import com.ishome.project.domain.OutboxEvent;
import com.ishome.project.domain.ProjectOwner;
import com.ishome.project.domain.port.FloorplanVisualsDispatch;
import com.ishome.project.testsupport.WiringFixture;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 串联全链（单测，内存仓储）：属主建项 → 两样齐了迁 M0.5 并派发 → 回流登记产物 + 写 outbox → 送达后迁 M1。
 *
 * <p>与 MilestoneEngineFlowTest 的分工：那份验里程碑引擎本身；这份验 2026-09-04 接线加的那几处——派发、回流、outbox。
 */
class WiringFlowTest {
  private static final ProjectOwner OWNER = new ProjectOwner("mock", "mock:local", "ou_test_1");
  private static final String FLOORPLAN_KEY = "uploads/" + "c".repeat(64) + "/original.png";

  private WiringFixture fixture;

  @BeforeEach
  void setUp() {
    fixture = new WiringFixture("http://project.local:8103/");
  }

  @Test
  void findOrCreateIsIdempotentPerOwner() {
    ProjectFindOrCreateResult first = fixture.projectAppService.findOrCreateProject(OWNER, null);
    ProjectFindOrCreateResult second = fixture.projectAppService.findOrCreateProject(OWNER, null);

    assertTrue(first.created());
    assertFalse(second.created());
    assertEquals(first.projectId(), second.projectId());
    assertEquals("M0", first.currentMilestone());
    assertEquals("v1", first.processVersion());
    assertEquals(OWNER, fixture.projectRepository.getById(first.projectId()).owner());
  }

  @Test
  void twoSlotsInOneBatchAdvanceToM05AndDispatchVisualsWithCallback() {
    String projectId = fixture.projectAppService.findOrCreateProject(OWNER, null).projectId();

    MilestoneProgressResult progress =
        fixture.projectAppService.fillSlots(
            projectId,
            List.of(
                slot(projectId, "floorplan", FLOORPLAN_KEY, CognitiveState.OBSERVED),
                slot(projectId, "building_area_sqm", "138", CognitiveState.OBSERVED),
                slot(projectId, "floor_area_ratio_percent", "80", CognitiveState.INFERRED)));

    assertTrue(progress.transitioned());
    assertEquals("M0.5", progress.currentMilestone());
    assertEquals(1, progress.createdTaskIds().size());
    String taskId = progress.createdTaskIds().get(0);

    assertEquals(1, fixture.visualsGateway.dispatched.size());
    FloorplanVisualsDispatch dispatch = fixture.visualsGateway.dispatched.get(0);
    assertEquals(taskId, dispatch.taskId());
    assertEquals(FLOORPLAN_KEY, dispatch.floorplanObjectKey());
    assertEquals(138.0, dispatch.buildingAreaSqm());
    assertEquals(80.0, dispatch.floorAreaRatioPercent());
    // 回调地址由本服务注入（自身地址 + 契约路径），尾部斜杠不重复
    assertEquals(
        "http://project.local:8103/api/v1/generation-tasks/" + taskId + "/result",
        dispatch.resultCallbackUrl());

    GenerationTask task = fixture.generationTaskRepository.getById(taskId);
    assertEquals(GenerationTaskStatus.RUNNING, task.status());
    assertEquals(FLOORPLAN_KEY, fixture.projectRepository.getById(projectId).floorplanRef());
  }

  @Test
  void dispatchFailureFailsTaskKeepsFactsAndTellsChat() {
    fixture.visualsGateway.failing(true);
    String projectId = fixture.projectAppService.findOrCreateProject(OWNER, null).projectId();

    MilestoneProgressResult progress = fillBothSlots(projectId);

    // 事实与迁移不回滚：里程碑真到了 M0.5，任务记 FAILED
    assertEquals("M0.5", progress.currentMilestone());
    GenerationTask task =
        fixture.generationTaskRepository.getById(progress.createdTaskIds().get(0));
    assertEquals(GenerationTaskStatus.FAILED, task.status());
    assertTrue(task.result().contains("dispatch-failed"));
    OutboxEvent event = onlyEvent();
    assertEquals(OutboxEvent.TYPE_GENERATION_TASK_FAILED, event.eventType());
  }

  @Test
  void completedResultRegistersArtifactsAndQueuesDeliverablesReady() throws Exception {
    String projectId = fixture.projectAppService.findOrCreateProject(OWNER, null).projectId();
    String taskId = fillBothSlots(projectId).createdTaskIds().get(0);

    GenerationTaskResultReceipt receipt =
        fixture.projectAppService.receiveGenerationTaskResult(completedResult(taskId));

    assertTrue(receipt.accepted());
    assertFalse(receipt.duplicate());
    assertEquals(6, receipt.registeredArtifactIds().size());

    List<Artifact> artifacts = fixture.artifactRepository.listByProjectId(projectId);
    assertEquals(6, artifacts.size());
    assertTrue(artifacts.stream().allMatch(a -> a.status() == ArtifactStatus.GENERATED));
    assertTrue(artifacts.stream().anyMatch(a -> a.artifactType().equals("vision_mood_image")));
    assertTrue(artifacts.stream().anyMatch(a -> a.artifactType().equals("floorplan_reading")));
    Artifact mood =
        artifacts.stream()
            .filter(a -> a.artifactType().equals("vision_mood_image"))
            .findFirst()
            .get();
    assertEquals("uploads/c/atmosphere-cream-journal-captioned.png", mood.storageUrl());
    assertTrue(mood.lineage().contains(taskId));
    assertTrue(mood.genParams().contains("cream-journal"));

    GenerationTask task = fixture.generationTaskRepository.getById(taskId);
    assertEquals(GenerationTaskStatus.COMPLETED, task.status());
    assertNotNull(task.artifactId());
    // 还没送到业主手里：仍在 M0.5
    assertEquals("M0.5", fixture.projectRepository.getById(projectId).currentMilestone());

    OutboxEvent event = onlyEvent();
    assertEquals(OutboxEvent.TYPE_DELIVERABLES_READY, event.eventType());
    JsonNode payload = fixture.objectMapper.readTree(event.payload());
    assertEquals(projectId, payload.get("project_id").asText());
    assertEquals(26, payload.get("delivery_id").asText().length());
    assertEquals("mock", payload.get("owner").get("channel_type").asText());
    assertEquals("ou_test_1", payload.get("owner").get("external_user_id").asText());
    // 只送三张图，且按情绪图 → 说明图 → 风格图的顺序；母版/几何/解析不送
    assertEquals(3, payload.get("deliverables").size());
    assertEquals(
        "vision_mood_image", payload.get("deliverables").get(0).get("artifact_type").asText());
    assertEquals(
        "vision_brief_image", payload.get("deliverables").get(1).get("artifact_type").asText());
    assertEquals(
        "vision_style_image", payload.get("deliverables").get(2).get("artifact_type").asText());
  }

  @Test
  void duplicateResultIsAcceptedOnceOnly() {
    String projectId = fixture.projectAppService.findOrCreateProject(OWNER, null).projectId();
    String taskId = fillBothSlots(projectId).createdTaskIds().get(0);
    fixture.projectAppService.receiveGenerationTaskResult(completedResult(taskId));

    GenerationTaskResultReceipt again =
        fixture.projectAppService.receiveGenerationTaskResult(completedResult(taskId));

    assertTrue(again.duplicate());
    assertEquals(6, fixture.artifactRepository.listByProjectId(projectId).size());
    assertEquals(1, fixture.outboxRepository.all().size());
  }

  @Test
  void failedResultFailsTaskAndQueuesFailureEvent() {
    String projectId = fixture.projectAppService.findOrCreateProject(OWNER, null).projectId();
    String taskId = fillBothSlots(projectId).createdTaskIds().get(0);

    fixture.projectAppService.receiveGenerationTaskResult(
        new GenerationTaskResultCommand(
            taskId,
            "failed",
            List.of(product("floorplan_geometry", "uploads/c/floorplan-geometry.json")),
            new GenerationFailure("plan-2d-render", "plan-master-failed=外圈闭合率 64%"),
            "wf",
            "run"));

    assertEquals(
        GenerationTaskStatus.FAILED, fixture.generationTaskRepository.getById(taskId).status());
    // 失败不登记半成品为产物（血缘留在任务的 result 里）
    assertEquals(0, fixture.artifactRepository.listByProjectId(projectId).size());
    OutboxEvent event = onlyEvent();
    assertEquals(OutboxEvent.TYPE_GENERATION_TASK_FAILED, event.eventType());
    assertTrue(event.payload().contains("plan-2d-render"));
  }

  @Test
  void presentedDeliverablesCompleteM05() {
    String projectId = fixture.projectAppService.findOrCreateProject(OWNER, null).projectId();
    String taskId = fillBothSlots(projectId).createdTaskIds().get(0);
    fixture.projectAppService.receiveGenerationTaskResult(completedResult(taskId));
    List<String> deliverableIds =
        fixture.artifactRepository.listByProjectId(projectId).stream()
            .filter(a -> a.artifactType().startsWith("vision_"))
            .map(Artifact::id)
            .toList();

    MilestoneProgressResult progress =
        fixture.projectAppService.markDeliverablesPresented(projectId, deliverableIds);

    assertTrue(progress.transitioned());
    assertEquals("M1", progress.currentMilestone());
    assertEquals(
        3,
        fixture.artifactRepository.listByProjectId(projectId).stream()
            .filter(a -> a.status() == ArtifactStatus.PRESENTED)
            .count());
  }

  // ---- 场景铺设 ----

  private MilestoneProgressResult fillBothSlots(String projectId) {
    return fixture.projectAppService.fillSlots(
        projectId,
        List.of(
            slot(projectId, "floorplan", FLOORPLAN_KEY, CognitiveState.OBSERVED),
            slot(projectId, "building_area_sqm", "138", CognitiveState.OBSERVED)));
  }

  private static SlotFilledCommand slot(
      String projectId, String key, String value, CognitiveState state) {
    return new SlotFilledCommand(projectId, key, value, state, "evt-" + key, 1.0);
  }

  private OutboxEvent onlyEvent() {
    List<OutboxEvent> events = fixture.outboxRepository.all();
    assertEquals(1, events.size());
    return events.get(0);
  }

  static GenerationTaskResultCommand completedResult(String taskId) {
    return new GenerationTaskResultCommand(
        taskId,
        "completed",
        List.of(
            product("floorplan_geometry", "uploads/c/floorplan-geometry.json"),
            product("plan_master", "uploads/c/plan-master.png"),
            product("brief_image", "uploads/c/plan-brief.png"),
            product("style_image", "uploads/c/atmosphere-lifestyle-notebook-handwritten.jpg"),
            new GenerationTaskProductCommand(
                "mood_image",
                "uploads/c/atmosphere-cream-journal-captioned.png",
                "image/png",
                Map.of("template_id", "cream-journal")),
            product("floorplan_reading", "uploads/c/floorplan-reading.json")),
        null,
        "floorplan-visuals-" + taskId,
        "run-1");
  }

  private static GenerationTaskProductCommand product(String product, String key) {
    return new GenerationTaskProductCommand(product, key, null, Map.of());
  }
}
