package com.ishome.project.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishome.project.application.OutboxRelayService;
import com.ishome.project.application.ProjectAppService;
import com.ishome.project.infrastructure.definition.ProcessDefinitionRepositoryImpl;

/** 串联用例的装配：内存仓储 + 记录型网关/呈现方，一次装齐（单测共用）。 */
public final class WiringFixture {
  public final InMemoryProjectRepository projectRepository = new InMemoryProjectRepository();
  public final InMemorySlotRepository slotRepository = new InMemorySlotRepository();
  public final InMemoryArtifactRepository artifactRepository = new InMemoryArtifactRepository();
  public final InMemoryGenerationTaskRepository generationTaskRepository =
      new InMemoryGenerationTaskRepository();
  public final InMemoryOutboxRepository outboxRepository = new InMemoryOutboxRepository();
  public final RecordingVisualsGateway visualsGateway = new RecordingVisualsGateway();
  public final RecordingDeliverablesPresenter presenter = new RecordingDeliverablesPresenter();
  public final ObjectMapper objectMapper = new ObjectMapper();
  public final ProjectAppService projectAppService;
  public final OutboxRelayService outboxRelayService;

  public WiringFixture() {
    this("http://127.0.0.1:8103");
  }

  public WiringFixture(String selfBaseUrl) {
    projectAppService =
        new ProjectAppService(
            projectRepository,
            slotRepository,
            artifactRepository,
            generationTaskRepository,
            new InMemoryRevisionLogRepository(),
            new InMemoryDecisionRepository(),
            new ProcessDefinitionRepositoryImpl(),
            outboxRepository,
            visualsGateway,
            objectMapper,
            selfBaseUrl,
            "v1");
    outboxRelayService =
        new OutboxRelayService(outboxRepository, presenter, projectAppService, objectMapper);
  }
}
