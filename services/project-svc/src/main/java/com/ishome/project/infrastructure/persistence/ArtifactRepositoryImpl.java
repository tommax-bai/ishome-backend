package com.ishome.project.infrastructure.persistence;

import com.ishome.project.domain.Artifact;
import com.ishome.project.domain.port.ArtifactRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** 内存实现（数据层任务后换 svc_project.artifacts）。 */
@Repository
public class ArtifactRepositoryImpl implements ArtifactRepository {

  private final Map<String, Artifact> store = new ConcurrentHashMap<>();

  @Override
  public void save(Artifact artifact) {
    store.put(artifact.id(), artifact);
  }

  @Override
  public Optional<Artifact> findById(String artifactId) {
    return Optional.ofNullable(store.get(artifactId));
  }

  @Override
  public List<Artifact> listByProjectId(String projectId) {
    return store.values().stream().filter(a -> a.projectId().equals(projectId)).toList();
  }
}
