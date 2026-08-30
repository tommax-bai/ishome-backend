package com.ishome.project.domain.rulebook;

/**
 * 本户**已经触发**的规则条目（图 v0.2 §2 报告数据包成员）：求值线按 {@link RuleAsset#trigger()} 判定完随包下发，
 * 成文线不重判触发（同"成文线不重判求值线"——判定在生产侧做完，与 {@link AnchorPresentation} 同机制）。
 *
 * <p>与 {@link RuleAsset} 的差别是**投影掉两个字段**：{@code trigger} 换成已判定的 {@link TriggerEvidence}
 * （原始触发条件是求值线的输入，成文线拿到它只会想自己再判一次）；{@code consumers} 不下发（成文线不认识 art-）。
 *
 * <p>用途限定＝"这一章该讲到什么"的输入，喂叙事推导步定主张；{@code content}/{@code rationale} 是**内部陈述句**， **禁止逐字进写作 prompt
 * 当句子抄**——现成句子进 prompt 就会被照抄（坑单 prompt 铁律一，与 persona 示范句 可抄性同病）。背书纪律不变：{@code calibration !=
 * calibrated} 的规则不构成判断句依据。
 */
public record TriggeredRule(
    String assetId,
    String layer,
    String content,
    String rationale,
    String severity,
    String calibration,
    TriggerEvidence triggeredBy) {}
