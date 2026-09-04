package com.ishome.project.testsupport;

import com.ishome.project.domain.OutboxEvent;
import com.ishome.project.domain.port.OutboxRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 内存假实现——仅供单测注入：按写入顺序保留，published 用集合记。 */
public class InMemoryOutboxRepository implements OutboxRepository {
  private final Map<String, OutboxEvent> store = new LinkedHashMap<>();
  private final Set<String> published = ConcurrentHashMap.newKeySet();

  @Override
  public void save(OutboxEvent event) {
    store.put(event.id(), event);
  }

  @Override
  public List<OutboxEvent> listUnpublished(int limit) {
    List<OutboxEvent> pending = new ArrayList<>();
    for (OutboxEvent event : store.values()) {
      if (!published.contains(event.id())) {
        pending.add(event);
      }
      if (pending.size() >= limit) {
        break;
      }
    }
    return pending;
  }

  @Override
  public void markPublished(String eventId) {
    published.add(eventId);
  }

  public List<OutboxEvent> all() {
    return new ArrayList<>(store.values());
  }

  public boolean isPublished(String eventId) {
    return published.contains(eventId);
  }
}
