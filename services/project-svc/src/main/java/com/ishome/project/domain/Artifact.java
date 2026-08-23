package com.ishome.project.domain;

/**
 * 产物登记（ArtifactRegistry，对齐文档 §5.1 svc_project.artifacts）。
 *
 * <p>project-svc 只持产物引用与生成参数血缘（storageUrl 指向 OSS），不持产物本体。 {@code genParams} / {@code lineage} 为结构化
 * JSON 字符串，落库后转 JSONB。
 */
public record Artifact(
    String id,
    String projectId,
    String milestone,
    String artifactType,
    int version,
    String storageUrl,
    String genParams,
    String lineage,
    ArtifactStatus status) {

  public Artifact withStatus(ArtifactStatus newStatus) {
    return new Artifact(
        id, projectId, milestone, artifactType, version, storageUrl, genParams, lineage, newStatus);
  }
}
