package com.ishome.project.domain.port;

import com.ishome.project.domain.Artifact;
import com.ishome.project.domain.ArtifactNotFoundException;
import java.util.List;
import java.util.Optional;

/** 产物登记仓储 port（ArtifactRegistry）。 */
public interface ArtifactRepository {

  void save(Artifact artifact);

  Optional<Artifact> findById(String artifactId);

  default Artifact getById(String artifactId) {
    return findById(artifactId).orElseThrow(() -> new ArtifactNotFoundException(artifactId));
  }

  List<Artifact> listByProjectId(String projectId);
}
