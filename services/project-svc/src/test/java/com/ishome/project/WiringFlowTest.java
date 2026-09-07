package com.ishome.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
 * 串联全链（单测，内存仓储）：属主建项 → 两样齐了迁 M0.5 并派发 → 回流登记产物 + 写 outbox → 送达后迁 M1； 图没出来时业主重发户型图 → 补派再来一次。
 *
 * <p>与 MilestoneEngineFlowTest 的分工：那份验里程碑引擎本身；这份验 2026-09-04 接线加的那几处——派发、回流、outbox， 以及 2026-09-07
 * 加的补派。
 */
class WiringFlowTest {
  private static final ProjectOwner OWNER = new ProjectOwner("mock", "mock:local", "ou_test_1");
  private static final String FLOORPLAN_KEY = "uploads/" + "c".repeat(64) + "/original.png";
  private static final String SECOND_FLOORPLAN_KEY = "uploads/" + "e".repeat(64) + "/original.png";

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

  // ---- 补派：该出的图没出来，业主重发一张户型图就再来一次（用户裁决 2026-09-07）----

  @Test
  void resentFloorplanAfterFailureMintsNewTaskAndDispatches() {
    String projectId = fixture.projectAppService.findOrCreateProject(OWNER, null).projectId();
    fixture.visualsGateway.failing(true);
    String failedTaskId = fillBothSlots(projectId).createdTaskIds().get(0);
    fixture.visualsGateway.failing(false);

    MilestoneProgressResult progress = resendFloorplan(projectId, SECOND_FLOORPLAN_KEY, "evt-2");

    // 没有迁移（M0.5 的判据要三件产物送达，一件都没有），但图重发了就再来一次
    assertFalse(progress.transitioned());
    assertEquals("M0.5", progress.currentMilestone());
    assertEquals(1, progress.createdTaskIds().size());
    String newTaskId = progress.createdTaskIds().get(0);
    assertNotEquals(failedTaskId, newTaskId);

    // 重跑不是重试：铸新号，旧那条 FAILED 一个字节不动
    GenerationTask failed = fixture.generationTaskRepository.getById(failedTaskId);
    assertEquals(GenerationTaskStatus.FAILED, failed.status());
    assertTrue(failed.result().contains("dispatch-failed"));

    GenerationTask minted = fixture.generationTaskRepository.getById(newTaskId);
    assertEquals(GenerationTaskStatus.RUNNING, minted.status());
    assertEquals("evt-2", minted.triggerEventId());
    // 派发用的是新发的那张图
    assertEquals(1, fixture.visualsGateway.dispatched.size());
    FloorplanVisualsDispatch dispatch = fixture.visualsGateway.dispatched.get(0);
    assertEquals(newTaskId, dispatch.taskId());
    assertEquals(SECOND_FLOORPLAN_KEY, dispatch.floorplanObjectKey());
    assertEquals(138.0, dispatch.buildingAreaSqm());
  }

  @Test
  void resentFloorplanWhileTaskInFlightMintsNothing() {
    String projectId = fixture.projectAppService.findOrCreateProject(OWNER, null).projectId();
    String runningTaskId = fillBothSlots(projectId).createdTaskIds().get(0);

    // 业主等不及，连发第二张：还在跑就不铸第二个（不重烧算力）
    MilestoneProgressResult progress = resendFloorplan(projectId, SECOND_FLOORPLAN_KEY, "evt-2");

    assertFalse(progress.transitioned());
    assertEquals(List.of(), progress.createdTaskIds());
    assertEquals(List.of(runningTaskId), taskIdsOf(projectId));
    assertEquals(1, fixture.visualsGateway.dispatched.size());
  }

  @Test
  void sameSourceEventRedeliveredMintsNothing() {
    String projectId = fixture.projectAppService.findOrCreateProject(OWNER, null).projectId();
    fixture.visualsGateway.failing(true);
    fillBothSlots(projectId); // 第一张（evt-floorplan）铸的任务当场 FAILED
    MilestoneProgressResult second = resendFloorplan(projectId, SECOND_FLOORPLAN_KEY, "evt-2");
    assertEquals(1, second.createdTaskIds().size());

    // 渠道把更早那条消息又投了一遍：evt-floorplan 已经铸过任务，不铸第二个
    MilestoneProgressResult replay = resendFloorplan(projectId, FLOORPLAN_KEY, "evt-floorplan");

    assertEquals(List.of(), replay.createdTaskIds());
    assertEquals(2, taskIdsOf(projectId).size());
  }

  @Test
  void sameBatchPostedTwiceMintsNothing() {
    String projectId = fixture.projectAppService.findOrCreateProject(OWNER, null).projectId();
    fixture.visualsGateway.failing(true);
    fillBothSlots(projectId);
    resendFloorplan(projectId, SECOND_FLOORPLAN_KEY, "evt-2");

    // 会话侧把同一条 POST 重投：槽位那行还是这条事件写的，不算"业主重发一张图"
    MilestoneProgressResult again = resendFloorplan(projectId, SECOND_FLOORPLAN_KEY, "evt-2");

    assertEquals(List.of(), again.createdTaskIds());
    assertEquals(2, taskIdsOf(projectId).size());
  }

  @Test
  void resentFloorplanAfterCompletedTaskMintsNothing() {
    String projectId = fixture.projectAppService.findOrCreateProject(OWNER, null).projectId();
    String taskId = fillBothSlots(projectId).createdTaskIds().get(0);
    fixture.projectAppService.receiveGenerationTaskResult(completedResult(taskId));
    assertEquals(
        GenerationTaskStatus.COMPLETED, fixture.generationTaskRepository.getById(taskId).status());

    // 图跑成功了（还没送到业主手里、仍在 M0.5）：不重烧算力
    MilestoneProgressResult progress = resendFloorplan(projectId, SECOND_FLOORPLAN_KEY, "evt-2");

    assertEquals("M0.5", progress.currentMilestone());
    assertEquals(List.of(), progress.createdTaskIds());
    assertEquals(List.of(taskId), taskIdsOf(projectId));
  }

  @Test
  void resentFloorplanAfterImagesPresentedMintsNothing() {
    String projectId = fixture.projectAppService.findOrCreateProject(OWNER, null).projectId();
    String taskId = fillBothSlots(projectId).createdTaskIds().get(0);
    fixture.projectAppService.receiveGenerationTaskResult(completedResult(taskId));
    fixture.projectAppService.markDeliverablesPresented(
        projectId,
        fixture.artifactRepository.listByProjectId(projectId).stream()
            .filter(a -> a.artifactType().startsWith("vision_"))
            .map(Artifact::id)
            .toList());

    // 图出来了、里程碑已迁到 M1，M1 的 on_enter 里没有 CREATE_TASK：补派条件天然不成立
    MilestoneProgressResult progress = resendFloorplan(projectId, SECOND_FLOORPLAN_KEY, "evt-2");

    assertEquals("M1", progress.currentMilestone());
    assertEquals(List.of(), progress.createdTaskIds());
    assertEquals(List.of(taskId), taskIdsOf(projectId));
  }

  @Test
  void batchWithoutFloorplanMintsNothing() {
    String projectId = fixture.projectAppService.findOrCreateProject(OWNER, null).projectId();
    fixture.visualsGateway.failing(true);
    String failedTaskId = fillBothSlots(projectId).createdTaskIds().get(0);
    fixture.visualsGateway.failing(false);

    // 闲聊轮：业主报了城市，没重发图——不补派
    MilestoneProgressResult progress =
        fixture.projectAppService.fillSlots(
            projectId,
            List.of(
                new SlotFilledCommand(
                    projectId, "city", "杭州", CognitiveState.OBSERVED, "evt-chat", 1.0)));

    assertEquals(List.of(), progress.createdTaskIds());
    assertEquals(List.of(failedTaskId), taskIdsOf(projectId));
    assertEquals(0, fixture.visualsGateway.dispatched.size());
  }

  @Test
  void completedWithoutDeliverablesFailsTaskSoRedispatchCanTakeOver() {
    String projectId = fixture.projectAppService.findOrCreateProject(OWNER, null).projectId();
    String taskId = fillBothSlots(projectId).createdTaskIds().get(0);

    // 编排侧说 completed、却一件该送的都没有：库里状态要和"告诉业主失败"一致
    fixture.projectAppService.receiveGenerationTaskResult(
        new GenerationTaskResultCommand(
            taskId,
            "completed",
            List.of(product("floorplan_geometry", "uploads/c/floorplan-geometry.json")),
            null,
            "wf",
            "run"));

    assertEquals(
        GenerationTaskStatus.FAILED, fixture.generationTaskRepository.getById(taskId).status());
    OutboxEvent event = onlyEvent();
    assertEquals(OutboxEvent.TYPE_GENERATION_TASK_FAILED, event.eventType());
    assertTrue(event.payload().contains("no-deliverables"));

    // 记 FAILED 之后补派接管得了它
    MilestoneProgressResult progress = resendFloorplan(projectId, SECOND_FLOORPLAN_KEY, "evt-2");
    assertEquals(1, progress.createdTaskIds().size());
  }

  // ---- 场景铺设 ----

  private MilestoneProgressResult fillBothSlots(String projectId) {
    return fixture.projectAppService.fillSlots(
        projectId,
        List.of(
            slot(projectId, "floorplan", FLOORPLAN_KEY, CognitiveState.OBSERVED),
            slot(projectId, "building_area_sqm", "138", CognitiveState.OBSERVED)));
  }

  /** 业主又发了一张户型图（同一张或换一张都算，判的是渠道事件 id 变没变）。 */
  private MilestoneProgressResult resendFloorplan(
      String projectId, String objectKey, String sourceEventId) {
    return fixture.projectAppService.fillSlots(
        projectId,
        List.of(
            new SlotFilledCommand(
                projectId, "floorplan", objectKey, CognitiveState.OBSERVED, sourceEventId, 1.0)));
  }

  private List<String> taskIdsOf(String projectId) {
    return fixture.generationTaskRepository.listByProjectId(projectId).stream()
        .map(GenerationTask::id)
        .toList();
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
