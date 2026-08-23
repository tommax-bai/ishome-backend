package com.ishome.project.infrastructure.persistence;

/**
 * svc_project.slots 持久化对象：status 列存认知状态六值字符串（CognitiveState 逐字一致）。 写路径为 (project_id, slot_key)
 * upsert（{@link SlotMapper#upsert}），不走 BaseMapper。
 */
public class SlotPO {

  private String id;
  private String projectId;
  private String slotKey;
  private String value;
  private String status;
  private String sourceEventId;
  private double confidence;
  private String stage;

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

  public String getSlotKey() {
    return slotKey;
  }

  public void setSlotKey(String slotKey) {
    this.slotKey = slotKey;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getSourceEventId() {
    return sourceEventId;
  }

  public void setSourceEventId(String sourceEventId) {
    this.sourceEventId = sourceEventId;
  }

  public double getConfidence() {
    return confidence;
  }

  public void setConfidence(double confidence) {
    this.confidence = confidence;
  }

  public String getStage() {
    return stage;
  }

  public void setStage(String stage) {
    this.stage = stage;
  }
}
