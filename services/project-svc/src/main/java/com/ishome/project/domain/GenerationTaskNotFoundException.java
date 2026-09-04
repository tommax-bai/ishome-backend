package com.ishome.project.domain;

/** 生成任务不存在——不是本服务派出去的任务，回流无从归位。 */
public class GenerationTaskNotFoundException extends RuntimeException {
  public GenerationTaskNotFoundException(String taskId) {
    super("生成任务不存在：" + taskId);
  }
}
