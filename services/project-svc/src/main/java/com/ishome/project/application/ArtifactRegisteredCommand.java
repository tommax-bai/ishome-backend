package com.ishome.project.application;

import com.ishome.project.domain.ArtifactStatus;

/**
 * 产物登记命令——genpipe 完成出图后注册产物（GENERATED），chat 送达用户后升为 PRESENTED。 确认不走本命令（确认 = artifact_confirmed
 * 业务事实，见 {@link ArtifactConfirmedCommand}）。
 */
public record ArtifactRegisteredCommand(
    String projectId,
    String artifactType,
    String storageUrl,
    String genParams,
    String lineage,
    ArtifactStatus status) {}
