package com.ishome.project.domain;

/**
 * 槽位真相（对齐文档 §5.1 svc_project.slots）——吸收原 svc_design.facts。
 *
 * <p>槽位抽取发生在 chat（schema 由流程定义分发），值以本表为唯一真相；chat 仅缓存用于组装上下文。 {@code stage} = 槽位落库时项目所处里程碑。量纲入名规则落在
 * slotKey 数据上（如 usable_area_sqm、 budget_range_cents，规范 §4.1）。
 */
public record Slot(
    String projectId,
    String slotKey,
    String value,
    CognitiveState cognitiveState,
    String sourceEventId,
    double confidence,
    String stage) {}
