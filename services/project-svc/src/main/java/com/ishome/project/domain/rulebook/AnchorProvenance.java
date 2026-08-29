package com.ishome.project.domain.rulebook;

import java.time.LocalDate;

/**
 * 落点依据（规则 4.10c「标注必挂」，v2.4 裁决 2026-08-29）：这个数从哪来、什么时候取的、过没过可核性门。
 *
 * <p>v2.4 取消了"隐藏"这一档——未校准与已过期的资产**照常进产物**，纪律形态从"没依据就别说"改成
 * "**说了就必须标**"。本记录就是那条纪律的载体：它随落点下发，成文线据此在同一页挂出依据标注， 页级比对门禁按 {@code annotationRequired}
 * 逐条核对（未标注即违规）。
 *
 * <p>{@code source} 为 {@code null} **是事实不是缺失**：规则 4.10 的"经验条目"定义就是"无外部依据、 靠行业判断"，它恒为 {@code
 * draft}、照常呈现、语域限建议口吻。标注照挂——渲染层据此说明"这是我们的 经验判断"，**不得编造一个来源**（§12 禁编造）。真正"求不出"的落点走 {@link
 * GapRecord}，两条信号不混。
 *
 * <p>{@code annotationRequired} 与 {@link ReportAnchor#degraded()} **不是同一件事**：degraded 只看可核性门，
 * 本字段还含时效越界（{@code effectiveTo} 早于本次求值基准日）。过期数据仍是它当时的真实行情，标了取数时间 业主自己会折算（规则 4.10c/5.15，v2.4
 * 推翻"单价过期只出占比不出金额"）。
 */
public record AnchorProvenance(
    String source,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String calibration,
    boolean annotationRequired) {}
