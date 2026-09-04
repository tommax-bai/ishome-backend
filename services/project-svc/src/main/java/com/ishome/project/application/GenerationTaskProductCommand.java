package com.ishome.project.application;

import java.util.Map;

/** 编排侧交回的一件产物：词表值 product + 私有桶对象键 + 内容类型 + 生成参数快照。 */
public record GenerationTaskProductCommand(
    String product, String objectKey, String contentType, Map<String, Object> genParams) {}
