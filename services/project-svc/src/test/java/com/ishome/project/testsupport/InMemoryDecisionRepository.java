package com.ishome.project.testsupport;

import com.ishome.project.domain.Decision;
import com.ishome.project.domain.port.DecisionRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 内存假实现——仅供单测注入。 */
public class InMemoryDecisionRepository implements DecisionRepository {

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
