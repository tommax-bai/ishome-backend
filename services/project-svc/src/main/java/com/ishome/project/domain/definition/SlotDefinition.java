package com.ishome.project.domain.definition;

import java.util.List;

/**
 * 槽位 schema（chat 消费切片）：key 带量纲后缀（如 usable_area_sqm、budget_range_cents）， extractionHint 供 chat
 * 抽取提示，options 供选择题式提问（enum 型槽位）。
 */
public record SlotDefinition(
    String key, String valueType, String extractionHint, List<String> options) {}
