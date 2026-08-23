package com.ishome.project.infrastructure.persistence;

import com.ishome.project.domain.RevisionLog;
import com.ishome.project.domain.port.RevisionLogRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Repository;

/** 内存实现（数据层任务后换 svc_project.revision_log）。 */
@Repository
public class RevisionLogRepositoryImpl implements RevisionLogRepository {

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
