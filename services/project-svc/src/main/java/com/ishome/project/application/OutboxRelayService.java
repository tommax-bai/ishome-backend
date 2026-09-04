package com.ishome.project.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishome.project.domain.GenerationFailure;
import com.ishome.project.domain.OutboxEvent;
import com.ishome.project.domain.ProjectOwner;
import com.ishome.project.domain.port.DeliverablesPresentation;
import com.ishome.project.domain.port.DeliverablesPresenter;
import com.ishome.project.domain.port.OutboxRepository;
import com.ishome.project.domain.port.PresentedDeliverable;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * outbox 中继：把未投递的事件送到会话侧（对齐 2.6 纪律的"中继"那一半）。
 *
 * <p>总线（RocketMQ）接入前，中继直接调会话侧 rpc（{@link DeliverablesPresenter}）；接入后本类改为发总线、 chat
 * 订阅——换的是这一处实现，事件不换名、写事件的那一侧不动。
 *
 * <p>一次一批、逐条投递；投递成功才标已发布，失败留在表里等下一轮（重投由会话侧按 delivery_id 幂等）。 网络调用在事务外——中继不该拿着数据库锁去等 gRPC。
 */
@Service
public class OutboxRelayService {
  private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);
  private static final int BATCH_SIZE = 20;

  private final OutboxRepository outboxRepository;
  private final DeliverablesPresenter deliverablesPresenter;
  private final ProjectAppService projectAppService;
  private final ObjectMapper objectMapper;

  public OutboxRelayService(
      OutboxRepository outboxRepository,
      DeliverablesPresenter deliverablesPresenter,
      ProjectAppService projectAppService,
      ObjectMapper objectMapper) {
    this.outboxRepository = outboxRepository;
    this.deliverablesPresenter = deliverablesPresenter;
    this.projectAppService = projectAppService;
    this.objectMapper = objectMapper;
  }

  /** 定时中继（间隔经 ishome.project.outbox.relay-interval-ms 配置，默认 2 秒）。 */
  @Scheduled(
      fixedDelayString = "${ishome.project.outbox.relay-interval-ms:2000}",
      initialDelayString = "${ishome.project.outbox.relay-initial-delay-ms:5000}")
  public void relayPending() {
    relayBatch();
  }

  /** 中继一批；返回本轮投递成功的事件数（测试与手动触发用）。 */
  public int relayBatch() {
    int relayed = 0;
    for (OutboxEvent event : outboxRepository.listUnpublished(BATCH_SIZE)) {
      try {
        if (relayOne(event)) {
          relayed++;
        }
      } catch (RuntimeException e) {
        // 留在表里等下一轮：中继失败不是丢事件的理由
        log.warn("outbox 事件投递失败，留待重试：id={} type={}", event.id(), event.eventType(), e);
      }
    }
    return relayed;
  }

  private boolean relayOne(OutboxEvent event) {
    DeliverablesPresentation presentation;
    try {
      presentation = toPresentation(event);
    } catch (JsonProcessingException | IllegalArgumentException e) {
      // 载荷本身坏了：重试也不会变好，标发布并记错，不让一条坏事件堵住整条队列
      log.error("outbox 事件载荷解析失败，跳过：id={} type={}", event.id(), event.eventType(), e);
      outboxRepository.markPublished(event.id());
      return false;
    }
    if (presentation == null) {
      log.warn("outbox 事件类型无中继路径，跳过：id={} type={}", event.id(), event.eventType());
      outboxRepository.markPublished(event.id());
      return false;
    }
    boolean delivered = deliverablesPresenter.present(presentation);
    if (!delivered) {
      return false;
    }
    if (!presentation.deliverables().isEmpty()) {
      projectAppService.markDeliverablesPresented(
          presentation.projectId(),
          presentation.deliverables().stream().map(PresentedDeliverable::artifactId).toList());
    }
    outboxRepository.markPublished(event.id());
    return true;
  }

  private DeliverablesPresentation toPresentation(OutboxEvent event)
      throws JsonProcessingException {
    JsonNode payload = objectMapper.readTree(event.payload());
    ProjectOwner owner =
        new ProjectOwner(
            payload.path("owner").path("channel_type").asText(),
            payload.path("owner").path("channel_instance").asText(),
            payload.path("owner").path("external_user_id").asText());
    String projectId = payload.path("project_id").asText();
    String deliveryId = payload.path("delivery_id").asText();
    String taskType = payload.path("task_type").asText("");
    switch (event.eventType()) {
      case OutboxEvent.TYPE_DELIVERABLES_READY -> {
        List<PresentedDeliverable> deliverables = new ArrayList<>();
        for (JsonNode item : payload.path("deliverables")) {
          deliverables.add(
              new PresentedDeliverable(
                  item.path("artifact_id").asText(),
                  item.path("artifact_type").asText(),
                  item.path("object_key").asText(),
                  item.path("caption").asText("")));
        }
        if (deliverables.isEmpty()) {
          throw new IllegalArgumentException("deliverables.ready 事件里一件产物都没有");
        }
        return new DeliverablesPresentation(
            deliveryId, projectId, owner, deliverables, null, taskType);
      }
      case OutboxEvent.TYPE_GENERATION_TASK_FAILED -> {
        GenerationFailure failure =
            new GenerationFailure(
                payload.path("failure").path("code").asText(""),
                payload.path("failure").path("detail").asText(""));
        return new DeliverablesPresentation(
            deliveryId, projectId, owner, List.of(), failure, taskType);
      }
      default -> {
        return null;
      }
    }
  }
}
