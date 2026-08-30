package com.ishome.project.domain.rulebook;

import java.util.Map;
import java.util.Optional;

/**
 * 触发判据（规范 §4.1 三层三触发）：判"这条规则对这一户成不成立"。
 *
 * <p><b>确定性谓词求值，纯代码</b>——关系与数字同族，都不由 LLM 决定（规范 v2.5 裁决 2026-08-29）。 判定在生产侧做完、结果随包下发，成文线只执行不重判（与
 * {@link AnchorPresentationPolicy}、 {@link AnchorProvenancePolicy} 同机制）。纯函数、无 IO。
 *
 * <p><b>首版两类有执行器</b>：
 *
 * <ol>
 *   <li>{@code always}：无条件成立，依据为空——无条件的事没有"因为"，编一个就是伪因果（规范 §12）；
 *   <li>{@code layout_feature}：{@code trigger.layout_feature: X} ⇔ 匿名画像的 {@code layoutFeatures}
 *       **含键 X**。**键存在即触发，值不参与匹配**（contracts {@code rulebook/layout_features.md} 契约原文）。
 * </ol>
 *
 * <p><b>值的用途是依据留痕不是匹配条件</b>：它进 {@link TriggerEvidence#evidence()}，报告里"因为你家阳台带 家政位"的数据由此而来（规则 4.3
 * 可追溯性的户型侧对应物）。故本类**不建映射表、不认识任何标记名**—— 映射表一旦存在就会与数据漂移（同城市档裁决 2026-08-29），标记名的闭集校验在**核验侧**拦在入库前
 * （{@code scripts/rulebook/verify_seeds.py}），另一侧由解析产出自校验，两侧见契约《layout_features.md》§四。
 *
 * <p><b>其余三类一律按未触发处理，各自的执行器随各自的事件补上</b>：{@code answer}（问卷答案触发）随 **需求采集问卷落地**扩展，{@code light} /
 * {@code switch}（点位触发）随**清单族产物（art-hydro-checklist 等） 落地**扩展。届时本类加一个分支、{@link RuleAsset}
 * 与契约一个字都不动——{@code trigger} 是原样带走的 jsonb， {@link TriggerEvidence#type()} 是词面不是枚举。
 *
 * <p><b>特征名缺失的 {@code layout_feature} 规则匹配不上任何键</b>（判为未触发）：它是"永远不触发且不报错" 的失效形态，承接它的是核验侧那道闭集校验（缺
 * {@code layout_feature} 键与键越界同为核验不通过），不是运行时—— 运行时抛出会让一条坏种子拖垮整份报告，而这条坏种子在入库前就该被拦下。
 */
public final class RuleTriggerPolicy {

  /** 无条件触发。 */
  private static final String TRIGGER_ALWAYS = "always";

  /** 户型特征触发：键存在即触发，值不参与匹配。 */
  private static final String TRIGGER_LAYOUT_FEATURE = "layout_feature";

  /** {@link #TRIGGER_LAYOUT_FEATURE} 触发条件里承载标记名的键（与快照 jsonb 逐字一致）。 */
  private static final String TRIGGER_FEATURE_KEY = "layout_feature";

  /** 触发类型在 trigger jsonb 里的键（与快照 jsonb 逐字一致）。 */
  private static final String TRIGGER_TYPE_KEY = "type";

  /**
   * 判定单条规则是否触发：触发则给出依据（{@link TriggerEvidence}），未触发给 {@link Optional#empty()}。
   *
   * @param layoutFeatures 匿名画像的户型特征标记集（键＝标记名、值＝该标记成立的依据）；{@code null} 视同空集
   */
  public Optional<TriggerEvidence> decide(RuleAsset rule, Map<String, String> layoutFeatures) {
    Map<String, Object> trigger = rule.trigger();
    if (trigger == null) {
      return Optional.empty();
    }
    String type = text(trigger.get(TRIGGER_TYPE_KEY));
    if (TRIGGER_ALWAYS.equals(type)) {
      return Optional.of(new TriggerEvidence(TRIGGER_ALWAYS, null, null));
    }
    if (!TRIGGER_LAYOUT_FEATURE.equals(type)) {
      return Optional.empty();
    }
    String feature = text(trigger.get(TRIGGER_FEATURE_KEY));
    Map<String, String> features = layoutFeatures == null ? Map.of() : layoutFeatures;
    if (feature == null || !features.containsKey(feature)) {
      return Optional.empty();
    }
    return Optional.of(new TriggerEvidence(TRIGGER_LAYOUT_FEATURE, feature, features.get(feature)));
  }

  /** 快照 jsonb 的标量取字符串：非字符串/空白一律 {@code null}——猜一个词面出来只会造出匹配不上的触发。 */
  private static String text(Object value) {
    if (!(value instanceof String text) || text.isBlank()) {
      return null;
    }
    return text;
  }
}
