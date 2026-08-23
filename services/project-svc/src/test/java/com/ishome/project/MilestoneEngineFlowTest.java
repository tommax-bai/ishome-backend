package com.ishome.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ishome.project.application.ArtifactConfirmedCommand;
import com.ishome.project.application.ArtifactRegisterResult;
import com.ishome.project.application.ArtifactRegisteredCommand;
import com.ishome.project.application.FeedbackReceivedCommand;
import com.ishome.project.application.MilestoneProgressResult;
import com.ishome.project.application.ProjectAppService;
import com.ishome.project.application.ProjectCreateCommand;
import com.ishome.project.application.ProjectCreatedResult;
import com.ishome.project.application.RevisionResult;
import com.ishome.project.application.SlotFilledCommand;
import com.ishome.project.domain.ArtifactStatus;
import com.ishome.project.domain.CognitiveState;
import com.ishome.project.domain.GenerationTask;
import com.ishome.project.domain.GenerationTaskStatus;
import com.ishome.project.infrastructure.definition.ProcessDefinitionRepositoryImpl;
import com.ishome.project.infrastructure.persistence.ArtifactRepositoryImpl;
import com.ishome.project.infrastructure.persistence.DecisionRepositoryImpl;
import com.ishome.project.infrastructure.persistence.GenerationTaskRepositoryImpl;
import com.ishome.project.infrastructure.persistence.ProjectRepositoryImpl;
import com.ishome.project.infrastructure.persistence.RevisionLogRepositoryImpl;
import com.ishome.project.infrastructure.persistence.SlotRepositoryImpl;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 里程碑引擎事件驱动全链路（真相在表，内存实现）：业务事实落库 → checkCompletion → 迁移 → on_enter 建任务；修订预算判定。 */
class MilestoneEngineFlowTest {

  private GenerationTaskRepositoryImpl generationTaskRepository;
  private ProjectAppService projectAppService;

  @BeforeEach
  void setUp() {
    generationTaskRepository = new GenerationTaskRepositoryImpl();
    projectAppService =
        new ProjectAppService(
            new ProjectRepositoryImpl(),
            new SlotRepositoryImpl(),
            new ArtifactRepositoryImpl(),
            generationTaskRepository,
            new RevisionLogRepositoryImpl(),
            new DecisionRepositoryImpl(),
            new ProcessDefinitionRepositoryImpl());
  }

  @Test
  void projectStartsAtM0WithoutTasks() {
    ProjectCreatedResult created = createProject();
    assertEquals("M0", created.currentMilestone());
    assertEquals("v1", created.processVersion());
    assertTrue(created.createdTaskIds().isEmpty());
  }

  @Test
  void m0SlotsCompleteTriggersTransitionToM05AndVisionTask() {
    ProjectCreatedResult created = createProject();
    String projectId = created.projectId();

    // 前四个槽位：判据部分满足，不迁移
    fill(projectId, "floorplan", "estate-fp-001");
    fill(projectId, "usable_area_sqm", "89");
    fill(projectId, "city", "杭州");
    MilestoneProgressResult partial = fill(projectId, "budget_range_cents", "20000000-30000000");
    assertFalse(partial.transitioned());
    assertEquals("M0", partial.currentMilestone());

    // 第五个槽位补齐：M0 判据满足 → 迁 M0.5 → on_enter 建愿景图任务
    MilestoneProgressResult full = fill(projectId, "family_structure", "三口之家");
    assertTrue(full.transitioned());
    assertEquals("M0.5", full.currentMilestone());
    assertEquals(List.of("M0.5"), full.enteredMilestones());
    assertEquals(1, full.createdTaskIds().size());

    List<GenerationTask> tasks = generationTaskRepository.listByProjectId(projectId);
    assertEquals(1, tasks.size());
    assertEquals("vision_image", tasks.get(0).taskType());
    assertEquals(GenerationTaskStatus.PENDING, tasks.get(0).status());
  }

  @Test
  void visionImagePresentedCompletesM05WithoutConfirmation() {
    String projectId = createProjectAtM05();

    // GENERATED 登记：判据（PRESENTED）未满足
    ArtifactRegisterResult generated =
        projectAppService.registerArtifact(
            new ArtifactRegisteredCommand(
                projectId,
                "vision_image",
                "oss://vision/v1.png",
                "{}",
                "{}",
                ArtifactStatus.GENERATED));
    assertFalse(generated.progress().transitioned());

    // 送达（PRESENTED）：M0.5 完成 → 迁 M1（无需确认——不求准求心动）
    ArtifactRegisterResult presented =
        projectAppService.registerArtifact(
            new ArtifactRegisteredCommand(
                projectId,
                "vision_image",
                "oss://vision/v2.png",
                "{}",
                "{}",
                ArtifactStatus.PRESENTED));
    assertTrue(presented.progress().transitioned());
    assertEquals("M1", presented.progress().currentMilestone());
  }

  @Test
  void m1RequiresUserConfirmedCognitiveState() {
    String projectId = createProjectAtM1();

    // proposed 不算确认——认知状态钳制
    fill(projectId, "style_direction", "奶油", CognitiveState.PROPOSED);
    fill(projectId, "functional_requirements", "收纳优先", CognitiveState.USER_CONFIRMED);
    MilestoneProgressResult notYet =
        fill(projectId, "hard_constraints", "不动承重墙", CognitiveState.USER_CONFIRMED);
    assertFalse(notYet.transitioned());

    // 风格方向经确认闭环升为 USER_CONFIRMED → M1 完成 → 迁 M2 → on_enter 建布局任务
    MilestoneProgressResult confirmed =
        fill(projectId, "style_direction", "奶油", CognitiveState.USER_CONFIRMED);
    assertTrue(confirmed.transitioned());
    assertEquals("M2", confirmed.currentMilestone());
    assertTrue(
        generationTaskRepository.listByProjectId(projectId).stream()
            .anyMatch(task -> task.taskType().equals("layout_plan")));
  }

  @Test
  void layoutConfirmationDrivesM2ToM3() {
    String projectId = createProjectAtM2();
    String artifactId =
        registerPresented(projectId, "layout_plan", "oss://layout/v1.png").artifactId();

    MilestoneProgressResult progress =
        projectAppService.confirmArtifact(
            new ArtifactConfirmedCommand(projectId, artifactId, "evt-confirm-1"));

    assertTrue(progress.transitioned());
    assertEquals("M3", progress.currentMilestone());
    assertTrue(
        generationTaskRepository.listByProjectId(projectId).stream()
            .anyMatch(task -> task.taskType().equals("space_render")));
  }

  @Test
  void revisionBudgetAllowsThreeRoundsThenExhausts() {
    String projectId = createProjectAtM2();
    String artifactId =
        registerPresented(projectId, "layout_plan", "oss://layout/v1.png").artifactId();

    for (int round = 1; round <= 3; round++) {
      RevisionResult result = feedback(projectId, artifactId, "layout_density");
      assertFalse(result.budgetExhausted());
      assertEquals(round, result.roundNo());
      assertEquals(3, result.budgetRounds());
      assertNotNull(result.taskId());
    }

    RevisionResult exhausted = feedback(projectId, artifactId, "circulation");
    assertTrue(exhausted.budgetExhausted());
    assertNull(exhausted.taskId());
  }

  @Test
  void revisionDimensionOutsideVocabularyIsRejected() {
    String projectId = createProjectAtM2();
    String artifactId =
        registerPresented(projectId, "layout_plan", "oss://layout/v1.png").artifactId();
    try {
      feedback(projectId, artifactId, "llm_invented_dimension");
      throw new AssertionError("词表外维度应被拒绝");
    } catch (IllegalArgumentException expected) {
      // LLM 自创值不采纳（chat 侧应已拦截，本服务防御性复验）
    }
  }

  @Test
  void milestoneWithoutRevisionRuleReportsExhaustedBudget() {
    String projectId = createProjectAtM05();
    RevisionResult result = feedback(projectId, null, "layout_density");
    assertTrue(result.budgetExhausted());
    assertEquals(0, result.budgetRounds());
  }

  // ---- 场景铺设 ----

  private ProjectCreatedResult createProject() {
    return projectAppService.createProject(new ProjectCreateCommand("u-1", null, "v1"));
  }

  private String createProjectAtM05() {
    ProjectCreatedResult created = createProject();
    fill(created.projectId(), "floorplan", "estate-fp-001");
    fill(created.projectId(), "usable_area_sqm", "89");
    fill(created.projectId(), "city", "杭州");
    fill(created.projectId(), "budget_range_cents", "20000000-30000000");
    fill(created.projectId(), "family_structure", "三口之家");
    return created.projectId();
  }

  private String createProjectAtM1() {
    String projectId = createProjectAtM05();
    registerPresented(projectId, "vision_image", "oss://vision/v1.png");
    return projectId;
  }

  private String createProjectAtM2() {
    String projectId = createProjectAtM1();
    fill(projectId, "style_direction", "奶油", CognitiveState.USER_CONFIRMED);
    fill(projectId, "functional_requirements", "收纳优先", CognitiveState.USER_CONFIRMED);
    fill(projectId, "hard_constraints", "不动承重墙", CognitiveState.USER_CONFIRMED);
    return projectId;
  }

  private MilestoneProgressResult fill(String projectId, String slotKey, String value) {
    return fill(projectId, slotKey, value, CognitiveState.OBSERVED);
  }

  private MilestoneProgressResult fill(
      String projectId, String slotKey, String value, CognitiveState state) {
    return projectAppService.fillSlot(
        new SlotFilledCommand(projectId, slotKey, value, state, "evt-" + slotKey, 0.95));
  }

  private ArtifactRegisterResult registerPresented(
      String projectId, String artifactType, String storageUrl) {
    return projectAppService.registerArtifact(
        new ArtifactRegisteredCommand(
            projectId, artifactType, storageUrl, "{}", "{}", ArtifactStatus.PRESENTED));
  }

  private RevisionResult feedback(String projectId, String artifactId, String dimension) {
    return projectAppService.receiveFeedback(
        new FeedbackReceivedCommand(projectId, artifactId, "餐厅", dimension, "decrease", "evt-fb"));
  }
}
