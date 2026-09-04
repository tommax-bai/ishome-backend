package com.ishome.project.domain.port;

/** 一件要送到业主手里的产物：登记 id + 产物类型（数据值）+ 私有桶对象键 + 随图一句说明（可空）。 */
public record PresentedDeliverable(
    String artifactId, String artifactType, String objectKey, String caption) {}
