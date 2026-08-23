package com.ishome.channel.domain;

/**
 * 渠道协议的接管形态——渠道属性的客观描述，字段保留在能力声明中仅因它是渠道协议属性； 本系统不使用该能力（V1.3 裁决），任何代码不得据此建立处理路径。
 *
 * <p>枚举值 UPPER_SNAKE 且与存储字符串逐字一致（规范 §2.1）。
 */
public enum HumanTakeover {
  NATIVE,
  GROUP,
  CONSOLE,
  NONE
}
