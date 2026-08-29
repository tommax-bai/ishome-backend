package com.ishome.project.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ishome.project.domain.rulebook.AnchorProvenance;
import com.ishome.project.domain.rulebook.AnchorProvenancePolicy;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 标注判据（规则 4.10c，v2.4 裁决 2026-08-29）：未过门或已过期 → 同页必须挂依据标注。
 *
 * <p>本类是隐藏档的替代物的判据面。判据只有两条且都确定性——这正是它能替代隐藏的理由：v2.3 的三条隐藏判据 里有一条是"判不准的一律隐藏"，那条判据本身判不准，机检落不了地。
 */
class AnchorProvenancePolicyTest {

  private final AnchorProvenancePolicy policy = new AnchorProvenancePolicy();

  private static final LocalDate TODAY = LocalDate.of(2026, 8, 29);

  @Test
  void calibratedAndInDateNeedsNoAnnotation() {
    AnchorProvenance provenance =
        policy.decide(
            "GB 50034-2013 表5.2.1",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2027, 1, 1),
            "calibrated",
            TODAY);

    assertFalse(provenance.annotationRequired());
    assertEquals("GB 50034-2013 表5.2.1", provenance.source());
  }

  @Test
  void draftRequiresAnnotation() {
    assertTrue(policy.decide("行业通行", null, null, "draft", TODAY).annotationRequired());
  }

  /** 经验条目（规则 4.10「无外部依据、靠行业判断」）：source 为 null 是事实不是缺失，标注照挂、禁编造来源。 */
  @Test
  void experienceItemWithoutSourceStillCarriesProvenance() {
    AnchorProvenance provenance = policy.decide(null, null, null, "draft", TODAY);

    assertTrue(provenance.annotationRequired());
    assertEquals(null, provenance.source());
  }

  /** 时效越界：过门的数也要标——标的是取数时间，业主自己会折算（规则 4.10c/5.15，v2.4 推翻"过期只出占比"）。 */
  @Test
  void expiredCalibratedAnchorRequiresAnnotation() {
    AnchorProvenance provenance =
        policy.decide(
            "2026Q1 建材报价",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 6, 30),
            "calibrated",
            TODAY);

    assertTrue(provenance.annotationRequired());
    assertEquals(LocalDate.of(2026, 1, 1), provenance.effectiveFrom());
  }

  /** 边界：有效期当天不算越界——越界判据是 effectiveTo **早于**基准日，当天仍在有效期内。 */
  @Test
  void lastValidDayIsNotStale() {
    assertFalse(
        policy
            .decide("2026Q3 建材报价", LocalDate.of(2026, 7, 1), TODAY, "calibrated", TODAY)
            .annotationRequired());
  }
}
