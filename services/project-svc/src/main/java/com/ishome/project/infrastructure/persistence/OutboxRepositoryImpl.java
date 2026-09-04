package com.ishome.project.infrastructure.persistence;

import com.ishome.project.domain.OutboxEvent;
import com.ishome.project.domain.port.OutboxRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** svc_project.outbox PG 实现。 */
@Repository
public class OutboxRepositoryImpl implements OutboxRepository {
  private final OutboxMapper outboxMapper;

  public OutboxRepositoryImpl(OutboxMapper outboxMapper) {
    this.outboxMapper = outboxMapper;
  }

  @Override
  public void save(OutboxEvent event) {
    OutboxPO po = new OutboxPO();
    po.setId(event.id());
    po.setAggregateType(event.aggregateType());
    po.setAggregateId(event.aggregateId());
    po.setEventType(event.eventType());
    po.setPayload(event.payload());
    outboxMapper.insert(po);
  }

  @Override
  public List<OutboxEvent> listUnpublished(int limit) {
    return outboxMapper.listUnpublished(limit).stream()
        .map(
            po ->
                new OutboxEvent(
                    po.getId(),
                    po.getAggregateType(),
                    po.getAggregateId(),
                    po.getEventType(),
                    po.getPayload()))
        .toList();
  }

  @Override
  public void markPublished(String eventId) {
    outboxMapper.markPublished(eventId);
  }
}
