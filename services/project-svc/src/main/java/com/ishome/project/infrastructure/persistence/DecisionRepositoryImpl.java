package com.ishome.project.infrastructure.persistence;

import com.ishome.project.domain.Decision;
import com.ishome.project.domain.port.DecisionRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Repository;

/** 内存实现（数据层任务后换 svc_project.decisions）。 */
@Repository
public class DecisionRepositoryImpl implements DecisionRepository {

  private final Map<String, List<Decision>> store = new ConcurrentHashMap<>();

  @Override
  public void save(Decision decision) {
    store.computeIfAbsent(decision.projectId(), key -> new CopyOnWriteArrayList<>()).add(decision);
  }

  @Override
  public List<Decision> listByProjectId(String projectId) {
    return List.copyOf(store.getOrDefault(projectId, List.of()));
  }
}
