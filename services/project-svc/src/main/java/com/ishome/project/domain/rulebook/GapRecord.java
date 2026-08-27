package com.ishome.project.domain.rulebook;

/**
 * 求值缺口记录（gap-，图 v0.2 §2：查不到 → 记录随产物回流，不阻塞）。{@code reason} 枚举三值： {@code missing_input}（匿名输入缺字段）/
 * {@code formula_not_implemented}（公式无可执行形态）/ {@code empty_definition}（参数无值无公式）。
 */
public record GapRecord(String lkpId, String reason, String detail) {}
