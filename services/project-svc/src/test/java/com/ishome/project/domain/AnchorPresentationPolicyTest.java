package com.ishome.project.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ishome.project.domain.rulebook.AnchorPresentation;
import com.ishome.project.domain.rulebook.AnchorPresentationPolicy;
import com.ishome.project.domain.rulebook.ArtifactEntitlement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 降档纪律判据（规则 4.10 消费侧门禁）：calibrated 照常、FREE 只降档、PAID 侧"能成区间的降档不成的隐藏"。 */
class AnchorPresentationPolicyTest {

  private final AnchorPresentationPolicy policy = new AnchorPresentationPolicy();

  private static Map<String, Object> range() {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("min", 900);
    value.put("max", 950);
    return value;
  }

  private static Map<String, Object> lowerBound() {
    return Map.of("min", 900);
  }

  private static Map<String, Object> pointValue() {
    return Map.of("v", 3000);
  }

  private static Map<String, Object> banded() {
    return Map.of("high", "±10%", "low", "±35%");
  }

  @Test
  void calibratedSupportsThesisRegardlessOfEntitlement() {
    for (ArtifactEntitlement entitlement : ArtifactEntitlement.values()) {
      AnchorPresentationPolicy.Verdict verdict =
          policy.decide("calibrated", "analysis", pointValue(), entitlement);
      assertEquals(AnchorPresentation.THESIS_SUPPORT, verdict.presentation());
      assertNull(verdict.withholdReason());
    }
  }

  /** FREE 不在规则 4.10 的 PAID 禁令射程内：未背书条目降档即可，但仍不得作判断句支点（规则 5.8）。 */
  @Test
  void freeDegradesButNeverWithholds() {
    assertEquals(
        AnchorPresentation.REFERENCE_ONLY,
        policy.decide("draft", "locating", pointValue(), ArtifactEntitlement.FREE).presentation());
    assertEquals(
        AnchorPresentation.REFERENCE_ONLY,
        policy
            .decide("needs_review", "analysis", banded(), ArtifactEntitlement.FREE)
            .presentation());
  }

  /** PAID + 区间形态 → 降档为参考级区间（规则 4.10"降档呈现（如参考级区间）"）；单边界同样算区间。 */
  @Test
  void paidDegradesValuesExpressibleAsRange() {
    assertEquals(
        AnchorPresentation.REFERENCE_ONLY,
        policy.decide("draft", "selection", range(), ArtifactEntitlement.PAID).presentation());
    assertEquals(
        AnchorPresentation.REFERENCE_ONLY,
        policy.decide("draft", "analysis", lowerBound(), ArtifactEntitlement.PAID).presentation());
  }

  /** PAID + 定位数字 → 隐藏："参考口吻的定位数字"不存在，业主会当施工指令读（规则 2.2/2.3）。 */
  @Test
  void paidWithholdsLocatingNumbersEvenWhenRangeShaped() {
    AnchorPresentationPolicy.Verdict verdict =
        policy.decide("draft", "locating", range(), ArtifactEntitlement.PAID);
    assertEquals(AnchorPresentation.WITHHELD, verdict.presentation());
    assertEquals(
        AnchorPresentationPolicy.WITHHOLD_REASON_LOCATING_NUMBER, verdict.withholdReason());
  }

  /** PAID + 点值/分档/空值 → 隐藏：拓宽成区间等于引擎自己编数字，宽度无依据（图 v0.2 §0）。 */
  @Test
  void paidWithholdsValuesWithoutRangeForm() {
    for (Map<String, Object> value :
        java.util.List.of(pointValue(), banded(), Map.<String, Object>of())) {
      AnchorPresentationPolicy.Verdict verdict =
          policy.decide("draft", "analysis", value, ArtifactEntitlement.PAID);
      assertEquals(AnchorPresentation.WITHHELD, verdict.presentation());
      assertEquals(
          AnchorPresentationPolicy.WITHHOLD_REASON_NO_RANGE_FORM, verdict.withholdReason());
    }
  }

  /** number_class 缺失（种子未标）时不享受任何豁免：走值形态判据，判不准就隐藏（规则 4.18 宁薄勿撑）。 */
  @Test
  void missingNumberClassFallsBackToValueShape() {
    assertEquals(
        AnchorPresentation.REFERENCE_ONLY,
        policy.decide("draft", null, range(), ArtifactEntitlement.PAID).presentation());
    assertEquals(
        AnchorPresentation.WITHHELD,
        policy.decide("draft", null, pointValue(), ArtifactEntitlement.PAID).presentation());
  }
}
