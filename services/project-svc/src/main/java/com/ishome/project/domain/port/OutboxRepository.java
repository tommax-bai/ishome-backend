package com.ishome.project.domain.port;

import com.ishome.project.domain.OutboxEvent;
import java.util.List;

/** outbox 仓储 port：写事件（与业务写同事务）、取未投递、标已投递。 */
public interface OutboxRepository {
  void save(OutboxEvent event);

  List<OutboxEvent> listUnpublished(int limit);

  void markPublished(String eventId);
}
