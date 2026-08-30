package com.ishome.project.domain.rulebook;

import java.util.List;
import java.util.Map;

/**
 * rule 形态资产（release 快照内投影，规则 4.9 五形态之一）：触发 → 条目（规范 §4.1 三层三触发）。
 *
 * <p>与 parameter/attribute 的分工：那两者产出**数字**（lkp- 落点），本形态产出**该讲什么**—— 触发成立的规则条目随包下发给成文线的叙事推导步定主张（图
 * v0.2 §3 第一步），不产出数字。
 *
 * <p>{@code trigger} 保持快照 jsonb **原样**（{@code {type, layout_feature?, question_id?,
 * answer_match?}}）， 不投影成分类型：触发类型是**只增**的闭集（首版 {@code always} / {@code layout_feature} 有执行器， 见
 * {@link RuleTriggerPolicy}），原样带走则新增类型时本记录与投影层一个字都不用动，只加执行器分支。 判定谁来做见 {@link
 * RuleTriggerPolicy}——本记录只搬运数据，不含判定。
 *
 * <p>{@code consumers} 是该条目的产物消费方（{@code art-*}）：留在资产上供核验侧查悬空引用，**不进报告数据包**—— 成文线不认识 art-（包内单元轴是
 * dom-，图 v0.2 §2）。{@code calibration} 随条目下发：draft 的规则不构成 判断句依据（断言预算只认 calibrated，同落点）。
 */
public record RuleAsset(
    String assetId,
    String layer,
    String content,
    String rationale,
    String severity,
    String calibration,
    Map<String, Object> trigger,
    List<String> consumers) {}
