package com.ishome.project.domain.rulebook;

import java.util.List;

/**
 * 域级 release 不可变快照（规则 4.12）在求值线的读取投影：parameters（lkp- 求值）、attributes（work_item 单价 → 落点投影，规则 5.15
 * 造价章）、rules（触发判定，规范 §4.1 三层三触发）、personas/checks/bannedTerms （成文线载荷——报告数据包必须自包含，图 v0.2
 * §0"不回查任何库"）。templates 投影随句式拼装落地时扩展，不动快照本体。
 *
 * <p>{@code rules} 是**判定的输入不是产物**：触发成立的那些才随包下发（{@link RuleTriggerPolicy} 判、装进 {@link
 * ReportDataPackage#triggeredRulesByDomain()}），没触发的连同触发条件一起留在快照里—— 成文线只拿到"已经成立"的结论，不重判触发。
 *
 * <p>{@code bannedTerms} = 快照内 vocabulary(kind=banned_term) 全部词面的平铺去重排序（跨域公共禁词已在发布时
 * 物化进各域快照）；persona 的 domain_extra 禁词在 {@link PersonaAsset#bannedTerms()} 内，消费侧合并。
 */
public record ReleaseSnapshot(
    String domain,
    String releaseTag,
    List<ParameterAsset> parameters,
    List<AttributeAsset> attributes,
    List<RuleAsset> rules,
    List<PersonaAsset> personas,
    List<CheckAsset> checks,
    List<String> bannedTerms) {

  public ReleaseRef ref() {
    return new ReleaseRef(domain, releaseTag);
  }
}
