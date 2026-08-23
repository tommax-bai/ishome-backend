package com.ishome.project.domain;

/**
 * 结构化修订指令 {target, dimension, direction}——chat 受限映射（修订维度词表枚举校验， LLM 自创值不采纳）后发来的业务事实载荷，本服务不理解自然语言。
 */
public record RevisionDirective(String target, String dimension, String direction) {}
