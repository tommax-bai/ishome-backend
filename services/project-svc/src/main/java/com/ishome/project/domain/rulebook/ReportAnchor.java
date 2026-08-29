package com.ishome.project.domain.rulebook;

import java.util.Map;

/**
 * 落点对象（图 v0.2 §2 报告数据包成员）：一个 lkp- 的求值结果。成文线的数字字段只能引用本对象， 机检可逐字段比对零漂移（图 v0.2 §3）。
 *
 * <p>{@code value} 为求值后的数值包（直取参数时=快照原值，公式时=代入匿名输入的计算结果）；{@code basisTag} + {@code source} =
 * 依据（release 引用 + 推导可见的出处）。
 *
 * <p>三字段的分工（消费侧门禁）：{@code degraded} 是**标记**——未过可核性门（{@code calibration != calibrated}）； {@code
 * presentation} 是**语域强制**——过不过可核性门决定能不能作判断句支点（{@link AnchorPresentationPolicy}，规则 4.10a/5.8）；
 * {@code provenance} 是**标注强制**——未过门或已过期的落点进正文时同页必须挂依据标注（{@link AnchorProvenancePolicy}，规则 4.10c）。
 * 成文线按后两者执行，不按 {@code degraded} 自由裁量。
 *
 * <p>{@code source} 与 {@code calibration} 两个平铺字段是 v2.4 之前的形态，**权威载体已是 {@code provenance}**（同值）；
 * 契约"只增不删"故保留，新消费方读 {@code provenance}。 管的时刻/生活翻译两字段待资产回路补齐后加入（当前种子无此数据，不预造）。
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
    AnchorProvenance provenance,
    AnchorPresentation presentation) {}
