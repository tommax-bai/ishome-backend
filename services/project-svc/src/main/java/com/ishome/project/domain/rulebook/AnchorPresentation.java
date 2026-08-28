package com.ishome.project.domain.rulebook;

/**
 * 落点呈现档位：规则 4.10 消费侧门禁的判定结果。{@code calibration} 是标记，本枚举是强制—— 它随报告数据包下发，成文线按它约束措辞与判断句支点，出口过检按它拦截。
 *
 * <p>命名说明：不落 {@code tier-} 命名空间（规则 1.7 前缀表里 {@code tier-} 已归规则库三层
 * tier-mandatory/practice/personal），本枚举是求值线对单个落点的呈现判定，与规则分层无关。
 */
public enum AnchorPresentation {
  /** 过可核性门（calibrated）：可作判断句支点——断言预算唯一合法的背书来源（规则 4.10a/5.8）。 */
  THESIS_SUPPORT,

  /** 降档为参考形态：只可以区间/参考口吻出现，**禁作判断句支点**（规则 4.10"降档呈现（如参考级区间）"）。 */
  REFERENCE_ONLY,

  /** 隐藏：值根本不下发，成文线无从引用（规则 4.10"或隐藏该条目"）；只在 withheldAnchors 留一条审计。 */
  WITHHELD
}
