package com.ishome.project.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/**
 * svc_project.artifacts 持久化对象。gen_params/lineage 为 JSON 字符串，落 jsonb 列 （数据源 stringtype=unspecified 由
 * PG 推断参数类型）。
 */
@TableName("artifacts")
public class ArtifactPO {

  @TableId(type = IdType.INPUT)
  private String id;

  private String projectId;
  private String milestone;
  private String artifactType;
  private int version;
  private String storageUrl;
  private String genParams;
  private String lineage;
  private String status;
  private OffsetDateTime updatedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public String getMilestone() {
    return milestone;
  }

  public void setMilestone(String milestone) {
    this.milestone = milestone;
  }

  public String getArtifactType() {
    return artifactType;
  }

  public void setArtifactType(String artifactType) {
    this.artifactType = artifactType;
  }

  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  public String getStorageUrl() {
    return storageUrl;
  }

  public void setStorageUrl(String storageUrl) {
    this.storageUrl = storageUrl;
  }

  public String getGenParams() {
    return genParams;
  }

  public void setGenParams(String genParams) {
    this.genParams = genParams;
  }

  public String getLineage() {
    return lineage;
  }

  public void setLineage(String lineage) {
    this.lineage = lineage;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
