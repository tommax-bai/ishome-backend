package com.ishome.project.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.ulid.UlidCreator;
import com.ishome.project.domain.Artifact;
import com.ishome.project.domain.ArtifactStatus;
import com.ishome.project.domain.Decision;
import com.ishome.project.domain.DecisionType;
import com.ishome.project.domain.GenerationFailure;
import com.ishome.project.domain.GenerationTask;
import com.ishome.project.domain.GenerationTaskStatus;
import com.ishome.project.domain.MilestoneCompletionPolicy;
import com.ishome.project.domain.MilestoneTransition;
import com.ishome.project.domain.OutboxEvent;
import com.ishome.project.domain.Project;
import com.ishome.project.domain.ProjectOwner;
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
import com.ishome.project.domain.port.FloorplanVisualsDispatch;
import com.ishome.project.domain.port.FloorplanVisualsGateway;
import com.ishome.project.domain.port.GenerationTaskRepository;
import com.ishome.project.domain.port.OutboxRepository;
import com.ishome.project.domain.port.PresentedDeliverable;
import com.ishome.project.domain.port.ProcessDefinitionRepository;
import com.ishome.project.domain.port.ProjectRepository;
import com.ishome.project.domain.port.RevisionLogRepository;
import com.ishome.project.domain.port.SlotRepository;
import com.ishome.project.domain.port.VisualsDispatchException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目用例编排：接收 chat 发来的结构化业务事实（slot_filled / artifact_confirmed / feedback_received）与 genpipe
 * 的产物登记，落库后触发里程碑引擎 checkCompletion。
 *
 * <p>链路单向：chat 发事实 → 本服务判定并建任务 → genpipe workflow 派发（走编排侧 HTTP 入口，裁决④）→ 产物登记 （编排侧按派发时注入的回调地址回流）→
 * 事件（outbox，同事务）→ chat 呈现（中继见 {@link OutboxRelayService}）。 里程碑迁移事件（Xxx{PastVerb}Event） 同样待总线落地后经
 * outbox 发出，当前以 decisions 表 MILESTONE_ENTER 记录真相。
 */
@Service
public class ProjectAppService {
  private static final Logger log = LoggerFactory.getLogger(ProjectAppService.class);

  /** 三张免费图的任务类型（数据值，与流程定义 M0.5 on_enter 的 task_type 逐字一致）。 */
  public static final String TASK_TYPE_VISION_IMAGE = "vision_image";

  static final String SLOT_FLOORPLAN = "floorplan";
  static final String SLOT_BUILDING_AREA_SQM = "building_area_sqm";
  static final String SLOT_FLOOR_AREA_RATIO_PERCENT = "floor_area_ratio_percent";
  static final String RESULT_CALLBACK_PATH = "/api/v1/generation-tasks/%s/result";

  private final ProjectRepository projectRepository;
  private final SlotRepository slotRepository;
  private final ArtifactRepository artifactRepository;
  private final GenerationTaskRepository generationTaskRepository;
  private final RevisionLogRepository revisionLogRepository;
  private final DecisionRepository decisionRepository;
  private final ProcessDefinitionRepository processDefinitionRepository;
  private final OutboxRepository outboxRepository;
  private final FloorplanVisualsGateway floorplanVisualsGateway;
  private final ObjectMapper objectMapper;
  private final String selfBaseUrl;
  private final String defaultProcessVersion;
  private final MilestoneCompletionPolicy milestoneCompletionPolicy =
      new MilestoneCompletionPolicy();

  public ProjectAppService(
      ProjectRepository projectRepository,
      SlotRepository slotRepository,
      ArtifactRepository artifactRepository,
      GenerationTaskRepository generationTaskRepository,
      RevisionLogRepository revisionLogRepository,
      DecisionRepository decisionRepository,
      ProcessDefinitionRepository processDefinitionRepository,
      OutboxRepository outboxRepository,
      FloorplanVisualsGateway floorplanVisualsGateway,
      ObjectMapper objectMapper,
      @Value("${ishome.project.self-base-url:http://127.0.0.1:8103}") String selfBaseUrl,
      @Value("${ishome.project.process-version:v1}") String defaultProcessVersion) {
    this.projectRepository = projectRepository;
    this.slotRepository = slotRepository;
    this.artifactRepository = artifactRepository;
    this.generationTaskRepository = generationTaskRepository;
    this.revisionLogRepository = revisionLogRepository;
    this.decisionRepository = decisionRepository;
    this.processDefinitionRepository = processDefinitionRepository;
    this.outboxRepository = outboxRepository;
    this.floorplanVisualsGateway = floorplanVisualsGateway;
    this.objectMapper = objectMapper;
    this.selfBaseUrl =
        selfBaseUrl.endsWith("/")
            ? selfBaseUrl.substring(0, selfBaseUrl.length() - 1)
            : selfBaseUrl;
    this.defaultProcessVersion = defaultProcessVersion;
  }

  /** 建项目：current_milestone = 流程定义首个里程碑，并执行其 on_enter 动作。 */
  @Transactional
  public ProjectCreatedResult createProject(ProjectCreateCommand command) {
    Project project =
        newProject(command.userId(), command.floorplanRef(), command.processVersion(), null);
    List<String> createdTaskIds = enterFirstMilestone(project);
    return new ProjectCreatedResult(
        project.id(), project.currentMilestone(), project.processVersion(), createdTaskIds);
  }

  /**
   * 按会话属主取项目，没有就建（幂等：同一属主至多一个进行中的项目）。
   *
   * <p>会话侧只知道属主三元组；项目 id 由本服务铸，回给会话侧缓存。identity 归一前 userId 以渠道侧用户标识占位。
   */
  @Transactional
  public ProjectFindOrCreateResult findOrCreateProject(ProjectOwner owner, String processVersion) {
    Optional<Project> existing = projectRepository.findActiveByOwner(owner);
    if (existing.isPresent()) {
      Project project = existing.get();
      return new ProjectFindOrCreateResult(
          project.id(), project.currentMilestone(), project.processVersion(), false);
    }
    String version =
        processVersion == null || processVersion.isBlank() ? defaultProcessVersion : processVersion;
    Project project = newProject(owner.externalUserId(), null, version, owner);
    enterFirstMilestone(project);
    return new ProjectFindOrCreateResult(
        project.id(), project.currentMilestone(), project.processVersion(), true);
  }

  /** 业务事实 slot_filled：槽位落库（真相在表）→ checkCompletion → 判据满足则迁移。 */
  @Transactional
  public MilestoneProgressResult fillSlot(SlotFilledCommand command) {
    return fillSlots(command.projectId(), List.of(command));
  }

  /**
   * 一批 slot_filled 同事务落完再判一次里程碑——面积与户型图常在同一轮到齐，逐条判会把一次迁移拆成两次半截的。 户型图槽位同时回写项目的 floorplanRef（私有桶对象键）。
   */
  @Transactional
  public MilestoneProgressResult fillSlots(String projectId, List<SlotFilledCommand> commands) {
    Project project = projectRepository.getById(projectId);
    for (SlotFilledCommand command : commands) {
      if (!projectId.equals(command.projectId())) {
        throw new IllegalArgumentException("槽位不属于本项目：" + command.slotKey());
      }
      slotRepository.save(
          new Slot(
              project.id(),
              command.slotKey(),
              command.value(),
              command.cognitiveState(),
              command.sourceEventId(),
              command.confidence(),
              project.currentMilestone()));
      if (SLOT_FLOORPLAN.equals(command.slotKey()) && command.value() != null) {
        project = project.withFloorplanRef(command.value());
        projectRepository.save(project);
      }
    }
    return advanceMilestones(project);
  }

  /** 产物登记（genpipe 完成 / chat 送达）：登记落库 → checkCompletion（如 M0.5 送达即迁移）。 */
  @Transactional
  public ArtifactRegisterResult registerArtifact(ArtifactRegisteredCommand command) {
    if (command.status() == ArtifactStatus.CONFIRMED) {
      throw new IllegalArgumentException("产物确认走 artifact_confirmed 业务事实，不走登记");
    }
    Project project = projectRepository.getById(command.projectId());
    Artifact artifact = saveNewArtifact(project, command);
    return new ArtifactRegisterResult(artifact.id(), advanceMilestones(project));
  }

  /**
   * 生成任务结果回流（编排侧按派发时注入的回调地址送来，contracts project.v1）。
   *
   * <p>completed → 逐件登记产物（GENERATED）→ 任务 COMPLETED → 写 outbox {@code project.deliverables.ready}
   * （同事务）；failed → 任务 FAILED → 写 {@code project.generation-task.failed}。同一 task_id 只收一次， 重投按
   * duplicate 收下不重复登记。产物词表是编排侧的，映射在 {@link VisualsProductCatalog}。
   */
  @Transactional
  public GenerationTaskResultReceipt receiveGenerationTaskResult(
      GenerationTaskResultCommand command) {
    GenerationTask task = generationTaskRepository.getById(command.taskId());
    if (task.isSettled()) {
      log.info("生成任务结果重投，按幂等收下：task={} status={}", task.id(), task.status());
      return new GenerationTaskResultReceipt(task.id(), true, true, List.of());
    }
    Project project = projectRepository.getById(task.projectId());
    String resultJson = toJson(resultSnapshot(command));

    if (!command.isCompleted()) {
      generationTaskRepository.save(task.withResult(GenerationTaskStatus.FAILED, null, resultJson));
      GenerationFailure failure =
          command.failure() != null
              ? command.failure()
              : new GenerationFailure("generation-failed", "编排侧回了 failed 但没说原因");
      recordTaskFailed(project, task, failure);
      return new GenerationTaskResultReceipt(task.id(), true, false, List.of());
    }

    List<String> artifactIds = new ArrayList<>();
    List<PresentedDeliverable> deliverables = new ArrayList<>();
    for (GenerationTaskProductCommand product : command.products()) {
      String artifactType =
          VisualsProductCatalog.artifactTypeOf(product.product())
              .orElseGet(
                  () -> {
                    log.warn("编排侧交回不认识的产物词 `{}`，照原词登记不呈现：task={}", product.product(), task.id());
                    return product.product();
                  });
      Artifact artifact =
          saveNewArtifact(
              project,
              new ArtifactRegisteredCommand(
                  project.id(),
                  artifactType,
                  product.objectKey(),
                  toJson(genParamsOf(product)),
                  toJson(lineageOf(task, command, product)),
                  ArtifactStatus.GENERATED));
      artifactIds.add(artifact.id());
      if (VisualsProductCatalog.isDeliverable(artifactType)) {
        deliverables.add(
            new PresentedDeliverable(artifact.id(), artifactType, product.objectKey(), ""));
      }
    }
    generationTaskRepository.save(
        task.withResult(
            GenerationTaskStatus.COMPLETED,
            artifactIds.isEmpty() ? null : artifactIds.get(0),
            resultJson));
    advanceMilestones(project);

    if (deliverables.isEmpty()) {
      // 编排侧说成功、却一件该送的都没有：对业主而言等于没做出来，按失败告知
      recordTaskFailed(
          project, task, new GenerationFailure("no-deliverables", "编排侧回了 completed，但没有一张该送给业主的图"));
    } else {
      deliverables.sort(
          Comparator.comparingInt(
              item -> VisualsProductCatalog.presentationOrder(item.artifactType())));
      recordDeliverablesReady(project, task, deliverables);
    }
    return new GenerationTaskResultReceipt(task.id(), true, false, artifactIds);
  }

  /** 会话侧确认已把产物发给业主：GENERATED → PRESENTED → checkCompletion（M0.5 送达即迁 M1）。 */
  @Transactional
  public MilestoneProgressResult markDeliverablesPresented(
      String projectId, List<String> artifactIds) {
    Project project = projectRepository.getById(projectId);
    for (String artifactId : artifactIds) {
      Artifact artifact = artifactRepository.getById(artifactId);
      if (artifact.status() == ArtifactStatus.GENERATED) {
        artifactRepository.save(artifact.withStatus(ArtifactStatus.PRESENTED));
      }
    }
    return advanceMilestones(project);
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

  // ---------------------------------------------------------------------------
  // 里程碑引擎
  // ---------------------------------------------------------------------------

  private Project newProject(
      String userId, String floorplanRef, String processVersion, ProjectOwner owner) {
    ProcessDefinition definition = processDefinitionRepository.getByVersion(processVersion);
    MilestoneDefinition first = definition.firstMilestone();
    Project project =
        new Project(
            newId(),
            userId,
            floorplanRef,
            definition.version(),
            first.id(),
            ProjectStatus.ACTIVE,
            owner);
    projectRepository.save(project);
    return project;
  }

  private List<String> enterFirstMilestone(Project project) {
    ProcessDefinition definition =
        processDefinitionRepository.getByVersion(project.processVersion());
    MilestoneDefinition first = definition.firstMilestone();
    recordMilestoneEnter(project, first.id(), null);
    return executeOnEnterActions(project, first.onEnterActions());
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

  /**
   * 执行 on_enter 动作声明（数据 → 编排）：CREATE_TASK 落 generation_task 业务真相；三张图任务当场派发。
   *
   * <p>派发失败不回滚事实与迁移（槽位是真的、里程碑也真到了），任务记 FAILED 并写失败事件让业主知道—— 挂在 PENDING 上等一个不会来的回流才是最坏的形态。
   */
  private List<String> executeOnEnterActions(Project project, List<OnEnterAction> actions) {
    List<String> createdTaskIds = new ArrayList<>();
    for (OnEnterAction action : actions) {
      switch (action.type()) {
        case CREATE_TASK -> {
          String taskType = action.params().get(OnEnterAction.PARAM_TASK_TYPE);
          String taskId =
              createGenerationTask(
                  project,
                  taskType,
                  "{\"milestone\":\"%s\"}".formatted(project.currentMilestone()));
          createdTaskIds.add(taskId);
          if (TASK_TYPE_VISION_IMAGE.equals(taskType)) {
            dispatchFloorplanVisuals(project, taskId);
          }
        }
      }
    }
    return createdTaskIds;
  }

  private void dispatchFloorplanVisuals(Project project, String taskId) {
    GenerationTask task = generationTaskRepository.getById(taskId);
    List<Slot> slots = slotRepository.listByProjectId(project.id());
    String floorplanObjectKey = slotValue(slots, SLOT_FLOORPLAN).orElse(project.floorplanRef());
    if (floorplanObjectKey == null || floorplanObjectKey.isBlank()) {
      failTask(project, task, new GenerationFailure("missing-floorplan", "项目没有户型图对象键，无从派发"));
      return;
    }
    FloorplanVisualsDispatch dispatch =
        new FloorplanVisualsDispatch(
            taskId,
            floorplanObjectKey,
            slotValue(slots, SLOT_BUILDING_AREA_SQM)
                .flatMap(ProjectAppService::parseDouble)
                .orElse(null),
            slotValue(slots, SLOT_FLOOR_AREA_RATIO_PERCENT)
                .flatMap(ProjectAppService::parseDouble)
                .orElse(null),
            selfBaseUrl + RESULT_CALLBACK_PATH.formatted(taskId));
    try {
      floorplanVisualsGateway.dispatch(dispatch);
    } catch (VisualsDispatchException e) {
      log.warn("三张图派发失败：project={} task={}", project.id(), taskId, e);
      failTask(project, task, new GenerationFailure("dispatch-failed", e.getMessage()));
      return;
    }
    generationTaskRepository.save(task.withStatus(GenerationTaskStatus.RUNNING));
  }

  private void failTask(Project project, GenerationTask task, GenerationFailure failure) {
    generationTaskRepository.save(
        task.withResult(
            GenerationTaskStatus.FAILED,
            null,
            toJson(Map.of("failure", Map.of("code", failure.code(), "detail", failure.detail())))));
    recordTaskFailed(project, task, failure);
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

  private Artifact saveNewArtifact(Project project, ArtifactRegisteredCommand command) {
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
    return artifact;
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

  // ---------------------------------------------------------------------------
  // outbox 事件（与业务写同事务）
  // ---------------------------------------------------------------------------

  private void recordDeliverablesReady(
      Project project, GenerationTask task, List<PresentedDeliverable> deliverables) {
    if (project.owner() == null) {
      log.warn("项目没有会话属主，产物无从送回：project={} task={}", project.id(), task.id());
      return;
    }
    Map<String, Object> payload = eventEnvelope(project, task);
    payload.put("delivery_id", newId());
    List<Map<String, Object>> items = new ArrayList<>();
    for (PresentedDeliverable item : deliverables) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("artifact_id", item.artifactId());
      entry.put("artifact_type", item.artifactType());
      entry.put("object_key", item.objectKey());
      entry.put("caption", item.caption());
      items.add(entry);
    }
    payload.put("deliverables", items);
    outboxRepository.save(
        new OutboxEvent(
            newId(),
            OutboxEvent.AGGREGATE_PROJECT,
            project.id(),
            OutboxEvent.TYPE_DELIVERABLES_READY,
            toJson(payload)));
  }

  private void recordTaskFailed(Project project, GenerationTask task, GenerationFailure failure) {
    if (project.owner() == null) {
      log.warn("项目没有会话属主，失败无从告知：project={} task={}", project.id(), task.id());
      return;
    }
    Map<String, Object> payload = eventEnvelope(project, task);
    payload.put("delivery_id", newId());
    payload.put("failure", Map.of("code", failure.code(), "detail", failure.detail()));
    outboxRepository.save(
        new OutboxEvent(
            newId(),
            OutboxEvent.AGGREGATE_PROJECT,
            project.id(),
            OutboxEvent.TYPE_GENERATION_TASK_FAILED,
            toJson(payload)));
  }

  private static Map<String, Object> eventEnvelope(Project project, GenerationTask task) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("project_id", project.id());
    payload.put("task_id", task.id());
    payload.put("task_type", task.taskType());
    Map<String, Object> owner = new LinkedHashMap<>();
    owner.put("channel_type", project.owner().channelType());
    owner.put("channel_instance", project.owner().channelInstance());
    owner.put("external_user_id", project.owner().externalUserId());
    payload.put("owner", owner);
    return payload;
  }

  private static Map<String, Object> resultSnapshot(GenerationTaskResultCommand command) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("status", command.status());
    List<Map<String, Object>> products = new ArrayList<>();
    for (GenerationTaskProductCommand product : command.products()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("product", product.product());
      entry.put("object_key", product.objectKey());
      entry.put("content_type", product.contentType());
      entry.put("gen_params", product.genParams() == null ? Map.of() : product.genParams());
      products.add(entry);
    }
    snapshot.put("products", products);
    if (command.failure() != null) {
      snapshot.put(
          "failure",
          Map.of("code", command.failure().code(), "detail", command.failure().detail()));
    }
    snapshot.put("workflow_id", command.workflowId());
    snapshot.put("run_id", command.runId());
    return snapshot;
  }

  private static Map<String, Object> genParamsOf(GenerationTaskProductCommand product) {
    Map<String, Object> genParams = new LinkedHashMap<>();
    if (product.genParams() != null) {
      genParams.putAll(product.genParams());
    }
    genParams.put("product", product.product());
    if (product.contentType() != null) {
      genParams.put("content_type", product.contentType());
    }
    return genParams;
  }

  private static Map<String, Object> lineageOf(
      GenerationTask task,
      GenerationTaskResultCommand command,
      GenerationTaskProductCommand product) {
    Map<String, Object> lineage = new LinkedHashMap<>();
    lineage.put("task_id", task.id());
    lineage.put("task_type", task.taskType());
    lineage.put("product", product.product());
    lineage.put("workflow_id", command.workflowId());
    lineage.put("run_id", command.runId());
    return lineage;
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("事件载荷序列化失败", e);
    }
  }

  private static Optional<String> slotValue(List<Slot> slots, String slotKey) {
    return slots.stream()
        .filter(slot -> slotKey.equals(slot.slotKey()))
        .map(Slot::value)
        .findFirst();
  }

  private static Optional<Double> parseDouble(String value) {
    try {
      return Optional.of(Double.parseDouble(value.trim()));
    } catch (NumberFormatException | NullPointerException e) {
      return Optional.empty();
    }
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
