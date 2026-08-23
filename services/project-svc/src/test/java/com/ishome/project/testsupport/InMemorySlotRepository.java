package com.ishome.project.testsupport;

import com.ishome.project.domain.Slot;
import com.ishome.project.domain.port.SlotRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 内存假实现：(projectId, slotKey) 幂等 upsert——仅供单测注入。 */
public class InMemorySlotRepository implements SlotRepository {

  private final Map<String, Map<String, Slot>> store = new ConcurrentHashMap<>();

  @Override
  public void save(Slot slot) {
    store
        .computeIfAbsent(slot.projectId(), key -> new ConcurrentHashMap<>())
        .put(slot.slotKey(), slot);
  }

  @Override
  public List<Slot> listByProjectId(String projectId) {
    return List.copyOf(store.getOrDefault(projectId, Map.of()).values());
  }
}
