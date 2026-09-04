package com.ishome.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ishome.project.application.SlotFilledCommand;
import com.ishome.project.domain.ArtifactStatus;
import com.ishome.project.domain.CognitiveState;
import com.ishome.project.domain.OutboxEvent;
import com.ishome.project.domain.ProjectOwner;
import com.ishome.project.domain.port.DeliverablesPresentation;
import com.ishome.project.testsupport.WiringFixture;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** outbox 中继：送到才标发布、送到即置 PRESENTED 并迁 M1；没送到留着；坏载荷跳过不堵队。 */
class OutboxRelayServiceTest {
  private static final ProjectOwner OWNER = new ProjectOwner("mock", "mock:local", "ou_test_2");

  private WiringFixture fixture;
  private String projectId;

  @BeforeEach
  void setUp() {
    fixture = new WiringFixture();
    projectId = fixture.projectAppService.findOrCreateProject(OWNER, null).projectId();
    String taskId =
        fixture
            .projectAppService
            .fillSlots(
                projectId,
                List.of(
                    new SlotFilledCommand(
                        projectId,
                        "floorplan",
                        "uploads/" + "d".repeat(64) + "/original.png",
                        CognitiveState.OBSERVED,
                        "e1",
                        1.0),
                    new SlotFilledCommand(
                        projectId, "building_area_sqm", "138", CognitiveState.OBSERVED, "e2", 1.0)))
            .createdTaskIds()
            .get(0);
    fixture.projectAppService.receiveGenerationTaskResult(WiringFlowTest.completedResult(taskId));
  }

  @Test
  void relayPresentsDeliverablesInOrderThenMarksPresentedAndPublished() {
    int relayed = fixture.outboxRelayService.relayBatch();

    assertEquals(1, relayed);
    assertEquals(1, fixture.presenter.presented.size());
    DeliverablesPresentation presentation = fixture.presenter.presented.get(0);
    assertEquals(projectId, presentation.projectId());
    assertEquals(OWNER, presentation.owner());
    assertNull(presentation.failure());
    assertEquals(
        List.of("vision_mood_image", "vision_brief_image", "vision_style_image"),
        presentation.deliverables().stream().map(d -> d.artifactType()).toList());
    assertTrue(presentation.deliverables().get(0).objectKey().endsWith("-captioned.png"));

    // 送到即 PRESENTED → M0.5 完成 → M1
    assertEquals("M1", fixture.projectRepository.getById(projectId).currentMilestone());
    assertEquals(
        3,
        fixture.artifactRepository.listByProjectId(projectId).stream()
            .filter(a -> a.status() == ArtifactStatus.PRESENTED)
            .count());
    OutboxEvent event = fixture.outboxRepository.all().get(0);
    assertTrue(fixture.outboxRepository.isPublished(event.id()));
    // 再中继一轮：没有未投递的了
    assertEquals(0, fixture.outboxRelayService.relayBatch());
  }

  @Test
  void undeliveredEventStaysForNextRound() {
    fixture.presenter.delivering(false);

    assertEquals(0, fixture.outboxRelayService.relayBatch());

    OutboxEvent event = fixture.outboxRepository.all().get(0);
    assertFalse(fixture.outboxRepository.isPublished(event.id()));
    assertEquals("M0.5", fixture.projectRepository.getById(projectId).currentMilestone());
  }

  @Test
  void failureEventIsPresentedAsFailureWithoutDeliverables() {
    fixture.outboxRepository.save(
        new OutboxEvent(
            "01EVTFAIL",
            OutboxEvent.AGGREGATE_PROJECT,
            projectId,
            OutboxEvent.TYPE_GENERATION_TASK_FAILED,
            """
            {"project_id":"%s","task_id":"t","task_type":"vision_image","delivery_id":"d",
             "owner":{"channel_type":"mock","channel_instance":"mock:local","external_user_id":"ou_test_2"},
             "failure":{"code":"plan-2d-render","detail":"外圈闭合率 64%%"}}
            """
                .formatted(projectId)));

    assertEquals(2, fixture.outboxRelayService.relayBatch());

    DeliverablesPresentation failure = fixture.presenter.presented.get(1);
    assertTrue(failure.deliverables().isEmpty());
    assertEquals("plan-2d-render", failure.failure().code());
    assertEquals("vision_image", failure.taskType());
    assertTrue(fixture.outboxRepository.isPublished("01EVTFAIL"));
  }

  @Test
  void corruptPayloadIsSkippedNotRetriedForever() {
    fixture.outboxRepository.save(
        new OutboxEvent(
            "01EVTBAD",
            OutboxEvent.AGGREGATE_PROJECT,
            projectId,
            OutboxEvent.TYPE_DELIVERABLES_READY,
            "{not json"));

    fixture.outboxRelayService.relayBatch();

    assertTrue(fixture.outboxRepository.isPublished("01EVTBAD"));
    assertEquals(1, fixture.presenter.presented.size());
  }
}
