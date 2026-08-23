package com.ishome.project.testsupport;

import com.ishome.project.domain.GenerationTask;
import com.ishome.project.domain.port.GenerationTaskRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 内存假实现——仅供单测注入。 */
public class InMemoryGenerationTaskRepository implements GenerationTaskRepository {

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
