package com.ishome.project.domain.rulebook;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 报告数据包（图 v0.2 §2）：求值线产物，成文线 input_snapshot 的内容本体——**自包含，成文线不回查任何库**： lkp- 落点对象 + gap- 缺口 + persona
 * 载荷（全文，非引用）+ cr- 判据 + 触发成立的规则条目 + 禁词表 + 匿名画像回显。 动作表随清单求值落地时加入。
 *
 * <p>{@code triggeredRulesByDomain} = 本户触发的规则条目按域分组（键取 dom- 去前缀形态，同 {@code personasByDomain}）： 判定由
 * {@link RuleTriggerPolicy} 在生产侧做完，成文线**不重判触发**（同"成文线不重判求值线"）。
 * 它是"这一章该讲到什么"的输入，喂叙事推导步定主张；条目正文是内部陈述句，**禁止逐字进写作 prompt 当句子抄** （坑单 prompt 铁律一）。触发类型首版只有 {@code
 * always} 与 {@code layout_feature}，其余三类的执行器与扩展 事件写在 {@link RuleTriggerPolicy}。
 *
 * <p>{@code entitlement} = 本包服务的产物权益档（调用方传入，见 {@link ArtifactEntitlement}）：产物权益的唯一口径，
 * 也是成文线复核门禁时的上下文。它是**产物属性不是用户属性**，不破匿名纪律。
 *
 * <p>{@code evaluatedOn} = 本次求值的基准日：时效越界（规则 4.10c）判定看它、不看运行时时钟，随包下发故可重放。 {@code withheldAnchors}
 * **已作废、恒空**（v2.4 裁决 2026-08-29 取消隐藏档）：字段按契约"只增不删"保留。 {@code lockedTextsByDomain} = 本产物必挂的锁定文案 ID
 * 集（contracts {@code registries/locked_texts.md}）： 当前只含**求值结果派生**的那部分（未过门定位数字 → 现场复核话术）；调用方按 art-
 * 传入的那部分待两条线接通时 并入同一 map（并集去重），届时本记录形态不变。
 *
 * <p>确定性纪律：anchors/withheldAnchors/gaps 按 lkpId、releases 按 domain、各域内资产按 assetId 排序—— 同输入字节级同输出（规则
 * 8.2 可重放，图 v0.2 §8 首批验证项）。契约投影：contracts rulebook/report_data_package.schema.json。
 */
public record ReportDataPackage(
    LocalDate evaluatedOn,
    ArtifactEntitlement entitlement,
    List<String> domains,
    List<ReleaseRef> releases,
    List<ReportAnchor> anchors,
    List<WithheldAnchor> withheldAnchors,
    List<GapRecord> gaps,
    Map<String, List<PersonaAsset>> personasByDomain,
    Map<String, List<CheckAsset>> checksByDomain,
    Map<String, List<TriggeredRule>> triggeredRulesByDomain,
    Map<String, List<String>> bannedTermsByDomain,
    Map<String, List<String>> lockedTextsByDomain,
    EvaluationInput anonymousProfile) {}
