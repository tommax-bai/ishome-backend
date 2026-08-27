package com.ishome.project.domain.rulebook;

import java.util.Map;

/**
 * parameter 形态资产（release 快照内投影）：lkp- 求值落点的定义。
 *
 * <p>{@code value} 为异构数值包（min/max/v/分档键，快照 jsonb 原样）；{@code formula} 为公式的文本形态—— 可执行形态在 {@link
 * RulebookEvaluator} 按 assetId 显式实现（数字不由 LLM 决定，图 v0.2 §0）。
 */
public record ParameterAsset(
    String assetId,
    String name,
    String numberClass,
    Map<String, Object> value,
    String formula,
    String unit,
    String calibration,
    String source,
    int version) {}
