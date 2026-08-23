package com.ishome.project.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/** svc_project.projects 持久化对象。created_at/updated_at 默认由 DB now() 填充，更新时回写 updated_at。 */
@TableName("projects")
public class ProjectPO {

  @TableId(type = IdType.INPUT)
  private String id;

  private String userId;
  private String floorplanRef;
  private String processVersion;
  private String currentMilestone;
  private String status;
  private OffsetDateTime updatedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getFloorplanRef() {
    return floorplanRef;
  }

  public void setFloorplanRef(String floorplanRef) {
    this.floorplanRef = floorplanRef;
  }

  public String getProcessVersion() {
    return processVersion;
  }

  public void setProcessVersion(String processVersion) {
    this.processVersion = processVersion;
  }

  public String getCurrentMilestone() {
    return currentMilestone;
  }

  public void setCurrentMilestone(String currentMilestone) {
    this.currentMilestone = currentMilestone;
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
