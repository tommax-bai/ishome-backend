package com.ishome.project.domain.rulebook;

import java.util.Map;

/**
 * 降档纪律的判据（规则 4.10 在消费侧的实装）：把 calibration 标记变成强制的呈现档位。
 *
 * <p>规则原文只给了结果——"未校准（draft）资产禁止进入 PAID 产物——只能降档呈现（如参考级区间）或隐藏该条目" ——没给"何时降档、何时隐藏"。本类补的就是这条判据，四条依据：
 *
 * <ol>
 *   <li><b>过门的照常</b>：{@code calibrated} → {@link AnchorPresentation#THESIS_SUPPORT}，与权益无关——
 *       它是断言预算唯一合法的背书来源（规则 4.10a/5.8）。
 *   <li><b>FREE 只降档不隐藏</b>：隐藏的出处是规则 4.10 那句 PAID 禁令，FREE 产物不在禁令射程内； 但未背书条目在 FREE 侧同样不得作判断句支点（规则 5.8
 *       断言预算是语言纪律，不分权益），故一律降档。
 *   <li><b>PAID 侧判"能不能以参考形态说出口"</b>：参考级的语域是"只出区间"（规则 5.8），于是判据落成
 *       <b>能表达成区间的降档，表达不成区间的隐藏</b>——两条确定性检查：
 *       <ul>
 *         <li>定位数字（{@code number_class = locating}）一律隐藏：定位数字只允许出现在 prec-exact 图纸， 业主会把它当施工指令读（规则
 *             2.2/2.3）。"参考口吻的定位数字"不存在，措辞限定拦不住它被拿去施工。
 *         <li>值形态不含 {@code min}/{@code max} 的（点值、分档映射等）一律隐藏：把点值人为拓宽成区间 等于引擎自己编数字（图 v0.2 §0 数字不由 LLM
 *             决定，同理不由引擎瞎凑），拓宽多宽没有任何依据。 含 min 或 max 的值本身就是区间或边界，原样以参考口吻出现即可，降档够用。
 *       </ul>
 *   <li><b>判不准的一律隐藏</b>：冷启动纪律是"宁可章节少、页数薄，不可用无背书的判断句撑密度"（规则 4.18/1.6）， 门禁失效的方向必须是少发而不是多发。
 * </ol>
 *
 * <p>纯函数、无 IO、不认识任何 {@code art-} 产物——权益档由调用方传入（{@link ArtifactEntitlement}）。
 */
public final class AnchorPresentationPolicy {

  /** 定位数字（规则 2.3 数字三分法）：施工定位用的坐标/距离/高度，只允许出现在 prec-exact 图纸。 */
  private static final String NUMBER_CLASS_LOCATING = "locating";

  private static final String CALIBRATION_CALIBRATED = "calibrated";

  private static final String RANGE_KEY_MIN = "min";

  private static final String RANGE_KEY_MAX = "max";

  /** 隐藏原因：定位数字无参考形态（规则 2.2/2.3）。随 withheldAnchors 回流，供获取回路排优先级。 */
  public static final String WITHHOLD_REASON_LOCATING_NUMBER = "locating_number";

  /** 隐藏原因：值非区间形态，降不成"参考级只出区间"的说法（规则 5.8）。 */
  public static final String WITHHOLD_REASON_NO_RANGE_FORM = "no_range_form";

  /** 判定结果：档位 + 隐藏原因（仅 {@link AnchorPresentation#WITHHELD} 非空，其余为 null）。 */
  public record Verdict(AnchorPresentation presentation, String withholdReason) {}

  private static final Verdict THESIS_SUPPORT =
      new Verdict(AnchorPresentation.THESIS_SUPPORT, null);

  private static final Verdict REFERENCE_ONLY =
      new Verdict(AnchorPresentation.REFERENCE_ONLY, null);

  public Verdict decide(
      String calibration,
      String numberClass,
      Map<String, Object> value,
      ArtifactEntitlement entitlement) {
    if (CALIBRATION_CALIBRATED.equals(calibration)) {
      return THESIS_SUPPORT;
    }
    if (entitlement == ArtifactEntitlement.FREE) {
      return REFERENCE_ONLY;
    }
    if (NUMBER_CLASS_LOCATING.equals(numberClass)) {
      return new Verdict(AnchorPresentation.WITHHELD, WITHHOLD_REASON_LOCATING_NUMBER);
    }
    if (hasRangeForm(value)) {
      return REFERENCE_ONLY;
    }
    return new Verdict(AnchorPresentation.WITHHELD, WITHHOLD_REASON_NO_RANGE_FORM);
  }

  /** 区间形态 = 值里有 min 或 max（含单边界，如主通道净宽的 {@code {min:900}}"不少于"说法）。 */
  private static boolean hasRangeForm(Map<String, Object> value) {
    return value != null && (value.containsKey(RANGE_KEY_MIN) || value.containsKey(RANGE_KEY_MAX));
  }
}
