package com.ishome.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.f4b6a3.ulid.UlidCreator;
import com.ishome.project.application.ProjectAppService;
import com.ishome.project.application.ProjectCreateCommand;
import com.ishome.project.application.ProjectCreatedResult;
import com.ishome.project.application.SlotFilledCommand;
import com.ishome.project.domain.Artifact;
import com.ishome.project.domain.ArtifactStatus;
import com.ishome.project.domain.CognitiveState;
import com.ishome.project.domain.Decision;
import com.ishome.project.domain.DecisionType;
import com.ishome.project.domain.GenerationTask;
import com.ishome.project.domain.GenerationTaskStatus;
import com.ishome.project.domain.Project;
import com.ishome.project.domain.RevisionDirective;
import com.ishome.project.domain.RevisionLog;
import com.ishome.project.domain.Slot;
import com.ishome.project.domain.port.ArtifactRepository;
import com.ishome.project.domain.port.DecisionRepository;
import com.ishome.project.domain.port.GenerationTaskRepository;
import com.ishome.project.domain.port.ProjectRepository;
import com.ishome.project.domain.port.RevisionLogRepository;
import com.ishome.project.domain.port.SlotRepository;
import com.ishome.project.testsupport.PostgresIntegrationTestSupport;
import com.ishome.shared.kernel.testsupport.EnabledIfLocalPostgres;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 仓储 PG 实跑（Flyway 迁移 + MyBatis 实现，独立 schema svc_project_it）：本地 PG（localhost:15432）
 * 可达才执行，不可达跳过。测试用例各自使用独立 ULID，测试间互不污染。
 */
@SpringBootTest
@EnabledIfLocalPostgres
@Import(PostgresIntegrationTestSupport.CleanMigrateConfig.class)
class ProjectPersistenceIntegrationTest {

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    PostgresIntegrationTestSupport.register(registry);
  }

  @Autowired ProjectAppService projectAppService;
  @Autowired ProjectRepository projectRepository;
  @Autowired SlotRepository slotRepository;
  @Autowired ArtifactRepository artifactRepository;
  @Autowired GenerationTaskRepository generationTaskRepository;
  @Autowired RevisionLogRepository revisionLogRepository;
  @Autowired DecisionRepository decisionRepository;

  /** 全链路对表：建项 + 五个 M0 槽位 → 迁 M0.5 → on_enter 建任务，全部真相可从 PG 读回。 */
  @Test
  void milestoneFlowPersistsTruthInTables() {
    ProjectCreatedResult created =
        projectAppService.createProject(new ProjectCreateCommand("u-it-1", null, "v1"));
    String projectId = created.projectId();

    fill(projectId, "floorplan", "estate-fp-001");
    fill(projectId, "usable_area_sqm", "89");
    fill(projectId, "city", "杭州");
    fill(projectId, "budget_range_cents", "20000000-30000000");
    fill(projectId, "family_structure", "三口之家");

    Project reloaded = projectRepository.getById(projectId);
    assertEquals("M0.5", reloaded.currentMilestone());

    List<Slot> slots = slotRepository.listByProjectId(projectId);
    assertEquals(5, slots.size());
    assertTrue(slots.stream().allMatch(slot -> slot.cognitiveState() == CognitiveState.OBSERVED));

    List<GenerationTask> tasks = generationTaskRepository.listByProjectId(projectId);
    assertEquals(1, tasks.size());
    assertEquals("vision_image", tasks.get(0).taskType());
    assertEquals(GenerationTaskStatus.PENDING, tasks.get(0).status());

    List<Decision> decisions = decisionRepository.listByProjectId(projectId);
    assertEquals(
        2,
        decisions.stream()
            .filter(decision -> decision.decisionType() == DecisionType.MILESTONE_ENTER)
            .count());
  }

  /** 槽位 (project_id, slot_key) upsert：后写覆盖前写，唯一键不重复成行。 */
  @Test
  void slotUpsertKeepsSingleRowPerKey() {
    String projectId = UlidCreator.getUlid().toString();
    slotRepository.save(
        new Slot(projectId, "style_direction", "奶油", CognitiveState.PROPOSED, "evt-1", 0.7, "M1"));
    slotRepository.save(
        new Slot(
            projectId,
            "style_direction",
            "奶油",
            CognitiveState.USER_CONFIRMED,
            "evt-2",
            0.99,
            "M1"));

    List<Slot> slots = slotRepository.listByProjectId(projectId);
    assertEquals(1, slots.size());
    assertEquals(CognitiveState.USER_CONFIRMED, slots.get(0).cognitiveState());
    assertEquals("evt-2", slots.get(0).sourceEventId());
    assertEquals(0.99, slots.get(0).confidence());
  }

  /** 产物登记回读 + 确认闭环回写 status（jsonb 列以 JSON 字符串写入）。 */
  @Test
  void artifactStatusRoundTrip() {
    String projectId = UlidCreator.getUlid().toString();
    String artifactId = UlidCreator.getUlid().toString();
    Artifact artifact =
        new Artifact(
            artifactId,
            projectId,
            "M2",
            "layout_plan",
            1,
            "oss://layout/v1.png",
            "{\"seed\":42}",
            "{\"parent\":null}",
            ArtifactStatus.PRESENTED);
    artifactRepository.save(artifact);
    artifactRepository.save(artifact.withStatus(ArtifactStatus.CONFIRMED));

    Artifact reloaded = artifactRepository.getById(artifactId);
    assertEquals(ArtifactStatus.CONFIRMED, reloaded.status());
    assertEquals(1, reloaded.version());
    // jsonb 回读经 PG 规范化（键值间距），语义等价断言
    assertEquals("{\"seed\":42}", reloaded.genParams().replace(" ", ""));
  }

  /** 修订预算判定依据：同项目同里程碑计数。 */
  @Test
  void revisionLogCountsByProjectAndMilestone() {
    String projectId = UlidCreator.getUlid().toString();
    RevisionDirective directive = new RevisionDirective("餐厅", "layout_density", "decrease");
    revisionLogRepository.save(new RevisionLog(projectId, "M2", 1, directive, "task-1"));
    revisionLogRepository.save(new RevisionLog(projectId, "M2", 2, directive, "task-2"));
    revisionLogRepository.save(new RevisionLog(projectId, "M3", 1, directive, "task-3"));

    assertEquals(2, revisionLogRepository.countByProjectIdAndMilestone(projectId, "M2"));
    assertEquals(1, revisionLogRepository.countByProjectIdAndMilestone(projectId, "M3"));
    assertEquals(0, revisionLogRepository.countByProjectIdAndMilestone(projectId, "M4"));
  }

  private void fill(String projectId, String slotKey, String value) {
    projectAppService.fillSlot(
        new SlotFilledCommand(
            projectId, slotKey, value, CognitiveState.OBSERVED, "evt-" + slotKey, 0.95));
  }
}
