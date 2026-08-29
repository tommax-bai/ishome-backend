package com.ishome.project.domain.rulebook;

/**
 * 落点呈现档位：规则 4.10a/5.8 消费侧门禁的判定结果。{@code calibration} 是标记，本枚举是强制——
 * 它随报告数据包下发，成文线按它约束措辞与判断句支点，出口过检按它拦截。
 *
 * <p>命名说明：不落 {@code tier-} 命名空间（规则 1.7 前缀表里 {@code tier-} 已归规则库三层
 * tier-mandatory/practice/personal），本枚举是求值线对单个落点的呈现判定，与规则分层无关。
 */
public enum AnchorPresentation {
  /** 过可核性门（calibrated）：可作判断句支点——断言预算唯一合法的背书来源（规则 4.10a/5.8）。 */
  THESIS_SUPPORT,

  /**
   * 未过可核性门：语域限建议口吻（"我们建议…"），**禁作断言预算的背书**（规则 4.10c）。
   *
   * <p>v2.4 起它**不再限制出现**：照常进正文、也可进主旨句，条件是同页挂依据标注（{@link AnchorProvenance}）。原第三值 {@code
   * WITHHELD}（隐藏）随 v2.4 裁决 2026-08-29 整档作废——契约的 {@code presentation} 枚举本就只有两值，故此处删除不动线上形态。
   */
  REFERENCE_ONLY
}
