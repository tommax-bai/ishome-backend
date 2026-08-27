package com.ishome.project.domain.rulebook;

import java.util.Map;

/**
 * 落点对象（图 v0.2 §2 报告数据包成员）：一个 lkp- 的求值结果。成文线的数字字段只能引用本对象， 机检可逐字段比对零漂移（图 v0.2 §3）。
 *
 * <p>{@code value} 为求值后的数值包（直取参数时=快照原值，公式时=代入匿名输入的计算结果）；{@code basisTag} + {@code source} =
 * 依据（release 引用 + 推导可见的出处）；{@code degraded} = 未过可核性门（calibration != calibrated），消费侧按规则 4.10/4.18
 * 降档或缺席，PAID 门禁在消费侧执行。 管的时刻/生活翻译两字段待资产回路补齐后加入（当前种子无此数据，不预造）。
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
    boolean degraded) {}
