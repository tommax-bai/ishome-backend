package com.ishome.project.domain.rulebook;

/**
 * **已作废、恒空**（v2.4 裁决 2026-08-29 取消隐藏档，规范 §14.9）。
 *
 * <p>字段按契约"只增不删"保留在 {@link ReportDataPackage} 上（生产方恒发空列表），消费侧不得据此拦截：
 * 未校准与已过期的落点现在照常进产物，纪律改由"标注必挂"承接（{@link AnchorProvenance}，规则 4.10c）。
 *
 * <p>原语义（供审计）：被纪律拿掉的落点——只留 id 与原因，不带值、不带 source、不带名称；与 {@link GapRecord} 分列，gap- 是"求不出"、withheld
 * 是"求出了但纪律不许发"。v2.4 之后"求出了但不许发"这件事不再存在， 两条回流信号由 {@code provenance}（求出了但没依据）与 {@code gaps}（求不出）各自承载。
 */
public record WithheldAnchor(String lkpId, String basisTag, String reason) {}
