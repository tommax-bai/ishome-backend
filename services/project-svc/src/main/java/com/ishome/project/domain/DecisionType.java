package com.ishome.project.domain;

/** 用户决策类型（对齐文档 §5.1 decisions：确认/否决/里程碑进入）。值 UPPER_SNAKE，与 DB 字符串一致。 */
public enum DecisionType {
  CONFIRM,
  REJECT,
  MILESTONE_ENTER
}
