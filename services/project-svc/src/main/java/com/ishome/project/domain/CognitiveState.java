package com.ishome.project.domain;

/**
 * 认知状态六值——与 contracts {@code ishome/common/v1/cognitive_state.proto} 词表一致 （glossary：禁止同义变体，如裸
 * confirmed）。值 UPPER_SNAKE，与 DB 存的字符串逐字一致（规范 §2.1）。
 *
 * <p>USER_CONFIRMED 仅由确认闭环授予（chat 识别确认 → artifact_confirmed / slot 确认事实）。
 */
public enum CognitiveState {
  OBSERVED,
  INFERRED,
  PROPOSED,
  USER_CONFIRMED,
  MEASURED,
  VERIFIED
}
