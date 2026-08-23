package com.ishome.project.infrastructure.persistence;

import com.ishome.project.domain.Project;
import com.ishome.project.domain.port.ProjectRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** 内存实现（数据层任务后换 MyBatis + svc_project.projects）。 */
@Repository
public class ProjectRepositoryImpl implements ProjectRepository {

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
