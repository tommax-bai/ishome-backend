package com.ishome.project.domain;

/**
 * 项目——项目域唯一真相的聚合入口（对齐文档 §5.1 svc_project.projects）。
 *
 * <p>{@code processVersion} 在项目创建时固化，全生命周期同一版（D10）；{@code currentMilestone} 只由里程碑引擎迁移（chat
 * 永不判里程碑）。{@code floorplanRef} = estate floorplanId 或私有上传引用。
 */
public record Project(
    String id,
    String userId,
    String floorplanRef,
    String processVersion,
    String currentMilestone,
    ProjectStatus status) {

  public Project withCurrentMilestone(String milestoneId) {
    return new Project(id, userId, floorplanRef, processVersion, milestoneId, status);
  }
}
