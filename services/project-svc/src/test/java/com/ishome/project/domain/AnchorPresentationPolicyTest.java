package com.ishome.project.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ishome.project.domain.rulebook.AnchorPresentation;
import com.ishome.project.domain.rulebook.AnchorPresentationPolicy;
import org.junit.jupiter.api.Test;

/**
 * 语域判据（规则 4.10a/5.8）：过可核性门的可作判断句支点，没过的语域限建议口吻。
 *
 * <p>用例只剩两条**是裁决的结果不是覆盖不足**：v2.4（2026-08-29）拆掉了隐藏档，本类原先的三条判据
 * （定位数字必隐藏、非区间形态必隐藏、判不准必隐藏）与它们依赖的权益档、值形态一并作废—— 未过门的落点现在照常进正文、进主旨句，风险由"同页标注"承接（见
 * AnchorProvenancePolicyTest）。 那三条判据的回归样本（分档区间的叶子、{min_w,min_d} 前缀边界、置信→宽度映射）随之失去被测对象；
 * 它们暴露的教训——**过拦与漏拦同样是失效**——记在规范 §14.8/§14.9 与交接文档，不靠留着死代码留存。
 */
class AnchorPresentationPolicyTest {

  private final AnchorPresentationPolicy policy = new AnchorPresentationPolicy();

  @Test
  void calibratedSupportsThesis() {
    assertEquals(AnchorPresentation.THESIS_SUPPORT, policy.decide("calibrated"));
  }

  /** 未过门（含 needs_review）：语域限建议口吻，禁作断言背书——但**不再限制它出现**。 */
  @Test
  void unbackedCalibrationsAreReferenceOnly() {
    assertEquals(AnchorPresentation.REFERENCE_ONLY, policy.decide("draft"));
    assertEquals(AnchorPresentation.REFERENCE_ONLY, policy.decide("needs_review"));
    assertEquals(AnchorPresentation.REFERENCE_ONLY, policy.decide(null));
  }
}
