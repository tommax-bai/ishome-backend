package com.ishome.project.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/** svc_project.generation_tasks 持久化对象：只记业务事实，执行语义在 Temporal。 */
@TableName("generation_tasks")
public class GenerationTaskPO {

  @TableId(type = IdType.INPUT)
  private String id;

  private String projectId;
  private String taskType;
  private String inputSnapshot;
  private String status;
  private String artifactId;
  private String result;
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

  public String getTaskType() {
    return taskType;
  }

  public void setTaskType(String taskType) {
    this.taskType = taskType;
  }

  public String getInputSnapshot() {
    return inputSnapshot;
  }

  public void setInputSnapshot(String inputSnapshot) {
    this.inputSnapshot = inputSnapshot;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public void setArtifactId(String artifactId) {
    this.artifactId = artifactId;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }
}
