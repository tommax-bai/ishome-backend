package com.ishome.project.domain;

/** 项目状态。值 UPPER_SNAKE，与 DB 字符串一致（规范 §2.1；枚举存字符串，技术架构 §6.4）。 */
public enum ProjectStatus {
  ACTIVE,
  COMPLETED
}
