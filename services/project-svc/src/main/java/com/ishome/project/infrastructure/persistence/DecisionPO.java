package com.ishome.project.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** svc_project.decisions 持久化对象（只增不改）：decision_type 存 DecisionType 字符串。 */
@TableName("decisions")
public class DecisionPO {

  @TableId(type = IdType.INPUT)
  private String id;

  private String projectId;
  private String decisionType;
  private String milestone;
  private String artifactId;
  private String sourceEventId;

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

  public String getDecisionType() {
    return decisionType;
  }

  public void setDecisionType(String decisionType) {
    this.decisionType = decisionType;
  }

  public String getMilestone() {
    return milestone;
  }

  public void setMilestone(String milestone) {
    this.milestone = milestone;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public void setArtifactId(String artifactId) {
    this.artifactId = artifactId;
  }

  public String getSourceEventId() {
    return sourceEventId;
  }

  public void setSourceEventId(String sourceEventId) {
    this.sourceEventId = sourceEventId;
  }
}
