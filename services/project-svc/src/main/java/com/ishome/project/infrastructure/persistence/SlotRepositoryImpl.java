package com.ishome.project.infrastructure.persistence;

import com.ishome.project.domain.Slot;
import com.ishome.project.domain.port.SlotRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** 内存实现：(projectId, slotKey) 幂等 upsert（数据层任务后换 svc_project.slots）。 */
@Repository
public class SlotRepositoryImpl implements SlotRepository {

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
