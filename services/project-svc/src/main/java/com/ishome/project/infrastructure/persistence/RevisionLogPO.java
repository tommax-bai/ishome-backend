package com.ishome.project.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** svc_project.revision_log 持久化对象（只增不改）：directive 为结构化修订指令 JSON。 */
@TableName("revision_log")
public class RevisionLogPO {

  @TableId(type = IdType.INPUT)
  private String id;

  private String projectId;
  private String milestone;
  private int roundNo;
  private String directive;
  private String taskId;

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

  public int getRoundNo() {
    return roundNo;
  }

  public void setRoundNo(int roundNo) {
    this.roundNo = roundNo;
  }

  public String getDirective() {
    return directive;
  }

  public void setDirective(String directive) {
    this.directive = directive;
  }

  public String getTaskId() {
    return taskId;
  }

  public void setTaskId(String taskId) {
    this.taskId = taskId;
  }
}
