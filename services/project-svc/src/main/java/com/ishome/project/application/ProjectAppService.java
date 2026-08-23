package com.ishome.project.application;

import com.github.f4b6a3.ulid.UlidCreator;
import com.ishome.project.domain.Artifact;
import com.ishome.project.domain.ArtifactStatus;
import com.ishome.project.domain.Decision;
import com.ishome.project.domain.DecisionType;
import com.ishome.project.domain.GenerationTask;
import com.ishome.project.domain.GenerationTaskStatus;
import com.ishome.project.domain.MilestoneCompletionPolicy;
import com.ishome.project.domain.MilestoneTransition;
import com.ishome.project.domain.Project;
import com.ishome.project.domain.ProjectStatus;
import com.ishome.project.domain.RevisionDirective;
import com.ishome.project.domain.RevisionLog;
import com.ishome.project.domain.Slot;
import com.ishome.project.domain.definition.MilestoneDefinition;
import com.ishome.project.domain.definition.OnEnterAction;
import com.ishome.project.domain.definition.ProcessDefinition;
import com.ishome.project.domain.definition.RevisionRule;
import com.ishome.project.domain.port.ArtifactRepository;
import com.ishome.project.domain.port.DecisionRepository;
import com.ishome.project.domain.port.GenerationTaskRepository;
import com.ishome.project.domain.port.ProcessDefinitionRepository;
import com.ishome.project.domain.port.ProjectRepository;
import com.ishome.project.domain.port.RevisionLogRepository;
import com.ishome.project.domain.port.SlotRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目用例编排：接收 chat 发来的结构化业务事实（slot_filled / artifact_confirmed / feedback_received）与 genpipe
 * 的产物登记，落库后触发里程碑引擎 checkCompletion。
 *
 * <p>链路单向：chat 发事实 → 本服务判定并建任务 → genpipe workflow 派发（启动经事件，outbox 随数据层任务接入）→ 产物登记 → 事件 → chat
 * 呈现。里程碑迁移事件（Xxx{PastVerb}Event） 同样待 outbox 落地后经总线发出，当前以 decisions 表 MILESTONE_ENTER 记录真相。
 */
@Service
public class ProjectAppService {

  private final ProjectRepository projectRepository;
  private final SlotRepository slotRepository;
  private final ArtifactRepository artifactRepository;
  private final GenerationTaskRepository generationTaskRepository;
  private final RevisionLogRepository revisionLogRepository;
  private final DecisionRepository decisionRepository;
  private final ProcessDefinitionRepository processDefinitionRepository;
  private final MilestoneCompletionPolicy milestoneCompletionPolicy =
      new MilestoneCompletionPolicy();

  public ProjectAppService(
      ProjectRepository projectRepository,
      SlotRepository slotRepository,
      ArtifactRepository artifactRepository,
      GenerationTaskRepository generationTaskRepository,
      RevisionLogRepository revisionLogRepository,
      DecisionRepository decisionRepository,
      ProcessDefinitionRepository processDefinitionRepository) {
    this.projectRepository = projectRepository;
    this.slotRepository = slotRepository;
    this.artifactRepository = artifactRepository;
    this.generationTaskRepository = generationTaskRepository;
    this.revisionLogRepository = revisionLogRepository;
    this.decisionRepository = decisionRepository;
    this.processDefinitionRepository = processDefinitionRepository;
  }

  /** 建项目：current_milestone = 流程定义首个里程碑，并执行其 on_enter 动作。 */
  @Transactional
  public ProjectCreatedResult createProject(ProjectCreateCommand command) {
    ProcessDefinition definition =
        processDefinitionRepository.getByVersion(command.processVersion());
    MilestoneDefinition first = definition.firstMilestone();
    Project project =
        new Project(
            newId(),
            command.userId(),
            command.floorplanRef(),
            definition.version(),
            first.id(),
            ProjectStatus.ACTIVE);
    projectRepository.save(project);
    recordMilestoneEnter(project, first.id(), null);
    List<String> createdTaskIds = executeOnEnterActions(project, first.onEnterActions());
    return new ProjectCreatedResult(project.id(), first.id(), definition.version(), createdTaskIds);
  }

  /** 业务事实 slot_filled：槽位落库（真相在表）→ checkCompletion → 判据满足则迁移。 */
  @Transactional
  public MilestoneProgressResult fillSlot(SlotFilledCommand command) {
    Project project = projectRepository.getById(command.projectId());
    slotRepository.save(
        new Slot(
            project.id(),
            command.slotKey(),
            command.value(),
            command.cognitiveState(),
            command.sourceEventId(),
            command.confidence(),
            project.currentMilestone()));
    return advanceMilestones(project);
  }

  /** 产物登记（genpipe 完成 / chat 送达）：登记落库 → checkCompletion（如 M0.5 送达即迁移）。 */
  @Transactional
  public ArtifactRegisterResult registerArtifact(ArtifactRegisteredCommand command) {
    if (command.status() == ArtifactStatus.CONFIRMED) {
      throw new IllegalArgumentException("产物确认走 artifact_confirmed 业务事实，不走登记");
    }
    Project project = projectRepository.getById(command.projectId());
    int nextVersion =
        (int)
                artifactRepository.listByProjectId(project.id()).stream()
                    .filter(existing -> existing.artifactType().equals(command.artifactType()))
                    .count()
            + 1;
    Artifact artifact =
        new Artifact(
            newId(),
            project.id(),
            project.currentMilestone(),
            command.artifactType(),
            nextVersion,
            command.storageUrl(),
            command.genParams(),
            command.lineage(),
            command.status());
    artifactRepository.save(artifact);
    return new ArtifactRegisterResult(artifact.id(), advanceMilestones(project));
  }

  /** 业务事实 artifact_confirmed（确认闭环）：置 CONFIRMED + 记决策 → checkCompletion。 */
  @Transactional
  public MilestoneProgressResult confirmArtifact(ArtifactConfirmedCommand command) {
    Project project = projectRepository.getById(command.projectId());
    Artifact artifact = artifactRepository.getById(command.artifactId());
    artifactRepository.save(artifact.withStatus(ArtifactStatus.CONFIRMED));
    decisionRepository.save(
        new Decision(
            newId(),
            project.id(),
            DecisionType.CONFIRM,
            project.currentMilestone(),
            artifact.id(),
            command.sourceEventId()));
    return advanceMilestones(project);
  }

  /**
   * 业务事实 feedback_received：修订预算判定（真相 = revision_log 计数 vs 流程定义软预算）。 有余额 → 建 revision
   * task（基于当前里程碑产物类型 + 结构化指令）并记 revision_log； 无余额 → budget_exhausted，不建任务，chat 依此收束话术。
   */
  @Transactional
  public RevisionResult receiveFeedback(FeedbackReceivedCommand command) {
    Project project = projectRepository.getById(command.projectId());
    ProcessDefinition definition =
        processDefinitionRepository.getByVersion(project.processVersion());
    MilestoneDefinition milestone = getMilestone(definition, project.currentMilestone());
    RevisionRule rule = milestone.revisionRule();
    if (rule == null) {
      return new RevisionResult(true, 0, 0, null);
    }
    if (!rule.dimensions().contains(command.dimension())) {
      throw new IllegalArgumentException(
          "修订维度不在流程定义词表内：" + command.dimension() + "（chat 侧枚举校验应已拦截）");
    }
    int usedRounds =
        revisionLogRepository.countByProjectIdAndMilestone(project.id(), milestone.id());
    if (usedRounds >= rule.budgetRounds()) {
      return new RevisionResult(true, usedRounds, rule.budgetRounds(), null);
    }
    int roundNo = usedRounds + 1;
    RevisionDirective directive =
        new RevisionDirective(command.target(), command.dimension(), command.direction());
    String taskId =
        createGenerationTask(
            project,
            milestone.producedArtifactType(),
            "{\"base_artifact_id\":\"%s\",\"target\":\"%s\",\"dimension\":\"%s\",\"direction\":\"%s\"}"
                .formatted(
                    command.artifactId(),
                    directive.target(),
                    directive.dimension(),
                    directive.direction()));
    revisionLogRepository.save(
        new RevisionLog(project.id(), milestone.id(), roundNo, directive, taskId));
    return new RevisionResult(false, roundNo, rule.budgetRounds(), taskId);
  }

  /**
   * 里程碑引擎推进：循环 checkCompletion 直到判据不满足（一次事实可连迁多个里程碑， 如机会性抽取把后续里程碑槽位提前补齐）。每次迁移：落库 → 记
   * MILESTONE_ENTER → 执行 on_enter。
   */
  private MilestoneProgressResult advanceMilestones(Project project) {
    ProcessDefinition definition =
        processDefinitionRepository.getByVersion(project.processVersion());
    List<String> enteredMilestones = new ArrayList<>();
    List<String> createdTaskIds = new ArrayList<>();
    Project current = project;
    while (true) {
      Optional<MilestoneTransition> transition =
          milestoneCompletionPolicy.checkCompletion(
              definition,
              current.currentMilestone(),
              slotRepository.listByProjectId(current.id()),
              artifactRepository.listByProjectId(current.id()));
      if (transition.isEmpty()) {
        break;
      }
      current = current.withCurrentMilestone(transition.get().toMilestoneId());
      projectRepository.save(current);
      recordMilestoneEnter(
          current, transition.get().toMilestoneId(), transition.get().fromMilestoneId());
      enteredMilestones.add(transition.get().toMilestoneId());
      createdTaskIds.addAll(executeOnEnterActions(current, transition.get().onEnterActions()));
    }
    return new MilestoneProgressResult(
        current.id(),
        current.currentMilestone(),
        !enteredMilestones.isEmpty(),
        enteredMilestones,
        createdTaskIds);
  }

  /** 执行 on_enter 动作声明（数据 → 编排）：CREATE_TASK 落 generation_task 业务真相。 */
  private List<String> executeOnEnterActions(Project project, List<OnEnterAction> actions) {
    List<String> createdTaskIds = new ArrayList<>();
    for (OnEnterAction action : actions) {
      switch (action.type()) {
        case CREATE_TASK -> {
          String taskType = action.params().get(OnEnterAction.PARAM_TASK_TYPE);
          createdTaskIds.add(
              createGenerationTask(
                  project,
                  taskType,
                  "{\"milestone\":\"%s\"}".formatted(project.currentMilestone())));
        }
      }
    }
    return createdTaskIds;
  }

  private String createGenerationTask(Project project, String taskType, String inputPayload) {
    String taskId = newId();
    generationTaskRepository.save(
        new GenerationTask(
            taskId,
            project.id(),
            taskType,
            "{\"project_id\":\"%s\",\"task_type\":\"%s\",\"input\":%s}"
                .formatted(project.id(), taskType, inputPayload),
            GenerationTaskStatus.PENDING,
            null));
    return taskId;
  }

  private void recordMilestoneEnter(Project project, String milestoneId, String sourceMilestoneId) {
    decisionRepository.save(
        new Decision(
            newId(),
            project.id(),
            DecisionType.MILESTONE_ENTER,
            milestoneId,
            null,
            sourceMilestoneId == null ? null : "milestone:" + sourceMilestoneId));
  }

  private MilestoneDefinition getMilestone(ProcessDefinition definition, String milestoneId) {
    return definition
        .findMilestone(milestoneId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "项目当前里程碑 " + milestoneId + " 不在流程定义 " + definition.version() + " 内"));
  }

  private String newId() {
    return UlidCreator.getUlid().toString();
  }
}
