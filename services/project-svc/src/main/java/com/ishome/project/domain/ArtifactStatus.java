package com.ishome.project.domain;

/**
 * 产物状态（对齐文档 §5.1 artifacts.status：generated|presented|confirmed|rejected）。 值 UPPER_SNAKE，与 DB
 * 字符串一致。
 */
public enum ArtifactStatus {
  GENERATED,
  PRESENTED,
  CONFIRMED,
  REJECTED
}
