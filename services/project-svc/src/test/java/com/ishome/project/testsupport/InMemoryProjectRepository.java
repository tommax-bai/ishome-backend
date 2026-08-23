package com.ishome.project.testsupport;

import com.ishome.project.domain.Project;
import com.ishome.project.domain.port.ProjectRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 内存假实现——仅供单测注入（真相在表的 PG 实现见 infrastructure.persistence）。 */
public class InMemoryProjectRepository implements ProjectRepository {

  private final Map<String, Project> store = new ConcurrentHashMap<>();

  @Override
  public void save(Project project) {
    store.put(project.id(), project);
  }

  @Override
  public Optional<Project> findById(String projectId) {
    return Optional.ofNullable(store.get(projectId));
  }
}
