package com.ishome.project.infrastructure.persistence;

import com.ishome.project.domain.GenerationTask;
import com.ishome.project.domain.port.GenerationTaskRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** 内存实现（数据层任务后换 svc_project.generation_tasks）。 */
@Repository
public class GenerationTaskRepositoryImpl implements GenerationTaskRepository {

  private final Map<String, GenerationTask> store = new ConcurrentHashMap<>();

  @Override
  public void save(GenerationTask task) {
    store.put(task.id(), task);
  }

  @Override
  public List<GenerationTask> listByProjectId(String projectId) {
    return store.values().stream().filter(t -> t.projectId().equals(projectId)).toList();
  }
}
