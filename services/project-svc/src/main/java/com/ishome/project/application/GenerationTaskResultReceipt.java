package com.ishome.project.application;

import java.util.List;

/** 回流收讫：duplicate=true 表示这个 task_id 的结果此前已收过、本次未重复登记。 */
public record GenerationTaskResultReceipt(
    String taskId, boolean accepted, boolean duplicate, List<String> registeredArtifactIds) {}
