package com.ishome.project.infrastructure.persistence;

import com.ishome.project.domain.Artifact;
import com.ishome.project.domain.ArtifactStatus;
import com.ishome.project.domain.port.ArtifactRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** svc_project.artifacts PG 实现：save 为按 id 的 insert-or-update（确认闭环回写 status）。 */
@Repository
public class ArtifactRepositoryImpl implements ArtifactRepository {

  private final ArtifactMapper artifactMapper;

  public ArtifactRepositoryImpl(ArtifactMapper artifactMapper) {
    this.artifactMapper = artifactMapper;
  }

  @Override
  public void save(Artifact artifact) {
    ArtifactPO po = toPo(artifact);
    if (artifactMapper.selectById(artifact.id()) == null) {
      artifactMapper.insert(po);
    } else {
      po.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
      artifactMapper.updateById(po);
    }
  }

  @Override
  public Optional<Artifact> findById(String artifactId) {
    return Optional.ofNullable(artifactMapper.findActiveById(artifactId)).map(this::toDomain);
  }

  @Override
  public List<Artifact> listByProjectId(String projectId) {
    return artifactMapper.listActiveByProjectId(projectId).stream().map(this::toDomain).toList();
  }

  private ArtifactPO toPo(Artifact artifact) {
    ArtifactPO po = new ArtifactPO();
    po.setId(artifact.id());
    po.setProjectId(artifact.projectId());
    po.setMilestone(artifact.milestone());
    po.setArtifactType(artifact.artifactType());
    po.setVersion(artifact.version());
    po.setStorageUrl(artifact.storageUrl());
    po.setGenParams(artifact.genParams());
    po.setLineage(artifact.lineage());
    po.setStatus(artifact.status().name());
    return po;
  }

  private Artifact toDomain(ArtifactPO po) {
    return new Artifact(
        po.getId(),
        po.getProjectId(),
        po.getMilestone(),
        po.getArtifactType(),
        po.getVersion(),
        po.getStorageUrl(),
        po.getGenParams(),
        po.getLineage(),
        ArtifactStatus.valueOf(po.getStatus()));
  }
}
