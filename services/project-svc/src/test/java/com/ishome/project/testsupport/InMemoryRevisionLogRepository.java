package com.ishome.project.testsupport;

import com.ishome.project.domain.RevisionLog;
import com.ishome.project.domain.port.RevisionLogRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 内存假实现——仅供单测注入。 */
public class InMemoryRevisionLogRepository implements RevisionLogRepository {

  private final Map<String, List<RevisionLog>> store = new ConcurrentHashMap<>();

  @Override
  public void save(RevisionLog revisionLog) {
    store
        .computeIfAbsent(revisionLog.projectId(), key -> new CopyOnWriteArrayList<>())
        .add(revisionLog);
  }

  @Override
  public int countByProjectIdAndMilestone(String projectId, String milestone) {
    return (int)
        store.getOrDefault(projectId, List.of()).stream()
            .filter(log -> log.milestone().equals(milestone))
            .count();
  }
}
