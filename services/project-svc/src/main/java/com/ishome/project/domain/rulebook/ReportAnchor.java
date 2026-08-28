package com.ishome.project.domain.rulebook;

import java.util.Map;

/**
 * 落点对象（图 v0.2 §2 报告数据包成员）：一个 lkp- 的求值结果。成文线的数字字段只能引用本对象， 机检可逐字段比对零漂移（图 v0.2 §3）。
 *
 * <p>{@code value} 为求值后的数值包（直取参数时=快照原值，公式时=代入匿名输入的计算结果）；{@code basisTag} + {@code source} =
 * 依据（release 引用 + 推导可见的出处）。
 *
 * <p>降档两字段的分工（规则 4.10 消费侧门禁）：{@code degraded} 是**标记**——未过可核性门（{@code calibration != calibrated}）；
 * {@code presentation} 是**强制**——标记叠加本次求值的产物权益档与值形态后的呈现判定（{@link AnchorPresentationPolicy}）。 成文线按
 * {@code presentation} 拦截，不按 {@code degraded} 自由裁量。{@link AnchorPresentation#WITHHELD} 的落点
 * 不会出现在这里——它们只在 {@link WithheldAnchor} 留一条审计。 管的时刻/生活翻译两字段待资产回路补齐后加入（当前种子无此数据，不预造）。
 */
public record ReportAnchor(
    String lkpId,
    String name,
    String numberClass,
    String unit,
    Map<String, Object> value,
    String basisTag,
    String source,
    String calibration,
    boolean degraded,
    AnchorPresentation presentation) {}
