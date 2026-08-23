package com.ishome.project.application;

/** 产物登记结果：新登记产物 id + 触发的里程碑推进（如 M0.5 愿景图送达即迁移）。 */
public record ArtifactRegisterResult(String artifactId, MilestoneProgressResult progress) {}
