package com.ishome.project.testsupport;

import com.ishome.project.domain.Artifact;
import com.ishome.project.domain.port.ArtifactRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 内存假实现——仅供单测注入。 */
public class InMemoryArtifactRepository implements ArtifactRepository {

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
