package com.ishome.project.application;

/**
 * 建项目命令。processVersion 创建时固化到项目，全生命周期同一版（D10）。 floorplanRef = estate
 * floorplanId（库命中优先）或私有上传引用（兜底），可空——M0 判据会等它。
 */
public record ProjectCreateCommand(String userId, String floorplanRef, String processVersion) {}
