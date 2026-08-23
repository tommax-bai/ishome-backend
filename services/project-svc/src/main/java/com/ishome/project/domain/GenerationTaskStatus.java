package com.ishome.project.domain;

/**
 * 生成任务业务状态（对齐文档 §5.1 generation_tasks）。执行、重试、超时语义在 Temporal， 此枚举只记业务事实。值 UPPER_SNAKE，与 DB 字符串一致。
 */
public enum GenerationTaskStatus {
  PENDING,
  RUNNING,
  COMPLETED,
  FAILED
}
