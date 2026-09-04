package com.ishome.project.domain;

/**
 * 项目——项目域唯一真相的聚合入口（对齐文档 §5.1 svc_project.projects）。
 *
 * <p>{@code processVersion} 在项目创建时固化，全生命周期同一版（D10）；{@code currentMilestone} 只由里程碑引擎迁移（chat
 * 永不判里程碑）。{@code floorplanRef} = estate floorplanId 或私有上传引用（户型图对象键）。 {@code owner}
 * 为会话属主三元组（2026-09-04 接线加）；identity 归一前的项目可为空。
 */
public record Project(
    String id,
    String userId,
    String floorplanRef,
    String processVersion,
    String currentMilestone,
    ProjectStatus status,
    ProjectOwner owner) {

  /** 无属主的旧形态构造（骨架期用例与迁移前数据）。 */
  public Project(
      String id,
      String userId,
      String floorplanRef,
      String processVersion,
      String currentMilestone,
      ProjectStatus status) {
    this(id, userId, floorplanRef, processVersion, currentMilestone, status, null);
  }

  public Project withCurrentMilestone(String milestoneId) {
    return new Project(id, userId, floorplanRef, processVersion, milestoneId, status, owner);
  }

  public Project withFloorplanRef(String newFloorplanRef) {
    return new Project(
        id, userId, newFloorplanRef, processVersion, currentMilestone, status, owner);
  }
}
