package com.ishome.project.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ishome.project.domain.rulebook.AnchorPresentation;
import com.ishome.project.domain.rulebook.AnchorProvenance;
import com.ishome.project.domain.rulebook.ArtifactEntitlement;
import com.ishome.project.domain.rulebook.CheckAsset;
import com.ishome.project.domain.rulebook.CheckExample;
import com.ishome.project.domain.rulebook.EvaluationInput;
import com.ishome.project.domain.rulebook.GapRecord;
import com.ishome.project.domain.rulebook.ParameterAsset;
import com.ishome.project.domain.rulebook.PersonaAsset;
import com.ishome.project.domain.rulebook.ReleaseSnapshot;
import com.ishome.project.domain.rulebook.ReportAnchor;
import com.ishome.project.domain.rulebook.ReportDataPackage;
import com.ishome.project.domain.rulebook.RulebookEvaluator;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** lkp- 求值纯函数：三条求值路径、降档门禁、可重放与顺序无关性（规则 8.2/4.10、图 v0.2 §0）。 */
class RulebookEvaluatorTest {

  private final RulebookEvaluator evaluator = new RulebookEvaluator();

  /** 求值基准日固定：它是入参不是时钟，测试里更不能取当天——取当天等于让断言随日历漂移。 */
  private static final LocalDate EVALUATED_ON = LocalDate.of(2026, 8, 29);

  private static ParameterAsset param(
      String id, String name, Map<String, Object> value, String formula, String calibration) {
    return new ParameterAsset(id, name, "analysis", value, formula, "mm", calibration, "测试源", 1);
  }

  private static final PersonaAsset PERSONA =
      new PersonaAsset(
          "persona-ergonomics",
          "你在为这一家人校核他们家的尺寸。",
          List.of(),
          List.of(Map.of("predicate", "通道净宽", "requires", List.of("lkp-passage-main"))),
          Map.of("domain_extra", List.of("人体工学")),
          1);

  private static final CheckAsset CHECK =
      new CheckAsset(
          "cr-weak-words",
          "regex_deny",
          List.of("正文"),
          "可能|建议考虑|也许",
          null,
          "分析级结论禁弱词（规则 5.9）",
          "规范规则 5.9",
          List.of(),
          List.of(),
          "active",
          1);

  /** 判官层判据（规则 4.17）：反例样例挂 cr- 之下、首批观察态——随包下发才谈得上判官在成文线可用。 */
  private static final CheckAsset JUDGE_CHECK =
      new CheckAsset(
          "cr-fabricated-fact",
          "semantic_judge",
          List.of("正文"),
          null,
          "关于这家人的事实只能来自匿名画像",
          "编造输入之外的家庭事实",
          "规范规则 4.3 + 图 v0.2 §0",
          List.of(),
          List.of(new CheckExample("你和你太太", "画像里没有家庭构成信息", "两个人同时用的时候")),
          "observing",
          1);

  private static final ReleaseSnapshot ERGONOMICS =
      new ReleaseSnapshot(
          "ergonomics",
          "ergonomics@v1",
          List.of(
              param("lkp-counter-height", "橱柜台面高", null, "主厨身高/2 + [50,100]", "draft"),
              param("lkp-wardrobe-rod", "衣柜挂杆高", null, "身高 × 1.2", "draft"),
              param("lkp-passage-main", "主通道净宽", Map.of("min", 900), "calibrated", "calibrated"),
              param("lkp-tv-distance", "电视观看距离", null, "屏高 × [3,4]", "draft"),
              param("lkp-mystery", "无可执行形态", null, "神秘公式", "draft"),
              param("lkp-empty", "空定义", null, null, "draft")),
          List.of(PERSONA),
          List.of(CHECK, JUDGE_CHECK),
          List.of("依据", "综合考量"));

  private static final EvaluationInput INPUT =
      new EvaluationInput(1700, 1780, null, null, Map.of("kitchen_shape", "U"));

  @Test
  void evaluatesFormulaAndPassThroughAnchors() {
    ReportDataPackage pkg =
        evaluator.evaluate(List.of(ERGONOMICS), INPUT, ArtifactEntitlement.FREE, EVALUATED_ON);

    ReportAnchor counter = anchor(pkg, "lkp-counter-height");
    assertEquals(Map.of("min", 900, "max", 950), counter.value());
    assertTrue(counter.degraded());
    assertEquals("ergonomics@v1", counter.basisTag());

    assertEquals(Map.of("v", 2136L), anchor(pkg, "lkp-wardrobe-rod").value());

    ReportAnchor passage = anchor(pkg, "lkp-passage-main");
    assertEquals(Map.of("min", 900), passage.value());
    assertFalse(passage.degraded());
  }

  @Test
  void recordsGapsInsteadOfBlocking() {
    ReportDataPackage pkg =
        evaluator.evaluate(List.of(ERGONOMICS), INPUT, ArtifactEntitlement.FREE, EVALUATED_ON);

    assertEquals(
        List.of("lkp-empty", "lkp-mystery", "lkp-tv-distance"),
        pkg.gaps().stream().map(GapRecord::lkpId).toList());
    assertEquals("empty_definition", gap(pkg, "lkp-empty").reason());
    assertEquals("formula_not_implemented", gap(pkg, "lkp-mystery").reason());
    assertEquals("missing_input", gap(pkg, "lkp-tv-distance").reason());
  }

  /** FREE 侧全量下发，但未背书条目一律降档——判断句支点只留给过可核性门的（规则 5.8/4.10a）。 */
  @Test
  void freeDeliversEveryAnchorButOnlyCalibratedSupportsThesis() {
    ReportDataPackage pkg =
        evaluator.evaluate(List.of(ERGONOMICS), INPUT, ArtifactEntitlement.FREE, EVALUATED_ON);

    assertEquals(ArtifactEntitlement.FREE, pkg.entitlement());
    assertEquals(List.of(), pkg.withheldAnchors());
    assertEquals(AnchorPresentation.THESIS_SUPPORT, anchor(pkg, "lkp-passage-main").presentation());
    assertEquals(
        AnchorPresentation.REFERENCE_ONLY, anchor(pkg, "lkp-counter-height").presentation());
    assertEquals(AnchorPresentation.REFERENCE_ONLY, anchor(pkg, "lkp-wardrobe-rod").presentation());
  }

  /**
   * PAID 侧与 FREE 侧下发同一批落点（v2.4 裁决 2026-08-29 取消隐藏档）：未过门的点值照常下发、 语域降为建议口吻，标注纪律接管风险；withheldAnchors
   * 恒空。
   *
   * <p>本用例原名 paidWithholdsUnbackedPointValuesAndDegradesRanges，断言的是"点值降不成区间故隐藏"——
   * 那条判据整条作废：藏起来的建议对业主等于没有，对系统等于没有行为信号（规范 §14.9）。
   */
  @Test
  void paidDeliversUnbackedAnchorsWithReferenceRegister() {
    ReportDataPackage pkg =
        evaluator.evaluate(List.of(ERGONOMICS), INPUT, ArtifactEntitlement.PAID, EVALUATED_ON);

    assertEquals(ArtifactEntitlement.PAID, pkg.entitlement());
    assertEquals(
        List.of("lkp-counter-height", "lkp-passage-main", "lkp-wardrobe-rod"),
        pkg.anchors().stream().map(ReportAnchor::lkpId).toList());
    assertEquals(List.of(), pkg.withheldAnchors());
    assertEquals(AnchorPresentation.REFERENCE_ONLY, anchor(pkg, "lkp-wardrobe-rod").presentation());
    assertTrue(anchor(pkg, "lkp-wardrobe-rod").provenance().annotationRequired());
    assertEquals(AnchorPresentation.THESIS_SUPPORT, anchor(pkg, "lkp-passage-main").presentation());
    // 求不出仍走 gap-：两类回流信号各走各的回路（规则 4.5），标注承接的是"求出了但没依据"
    assertEquals(3, pkg.gaps().size());
  }

  private static ReleaseSnapshot locatingSnapshot() {
    return locatingSnapshot("draft");
  }

  private static ReleaseSnapshot locatingSnapshot(String calibration) {
    return new ReleaseSnapshot(
        "ergonomics",
        "ergonomics@v1",
        List.of(
            new ParameterAsset(
                "lkp-socket-height",
                "常用插座高度",
                "locating",
                Map.of("min", 300, "max", 350),
                null,
                "mm",
                calibration,
                "行业通行",
                1)),
        List.of(),
        List.of(),
        List.of());
  }

  /** 未过门的定位数字在 PAID 侧照常下发（v2.4：原"参考口吻的定位数字不存在"故必隐藏，整条作废）。 风险改由两件事承接：同页依据标注 + 派生必挂的现场复核话术。 */
  @Test
  void paidDeliversUnbackedLocatingNumbersWithSiteCheck() {
    ReportDataPackage pkg =
        evaluator.evaluate(
            List.of(locatingSnapshot()), INPUT, ArtifactEntitlement.PAID, EVALUATED_ON);

    assertEquals(
        List.of("lkp-socket-height"), pkg.anchors().stream().map(ReportAnchor::lkpId).toList());
    assertEquals(List.of(), pkg.withheldAnchors());
    assertTrue(anchor(pkg, "lkp-socket-height").provenance().annotationRequired());
    assertEquals(Map.of("ergonomics", List.of("GUIDE_SITE_CHECK")), pkg.lockedTextsByDomain());
  }

  /**
   * 标注纪律随落点下发（规则 4.10c，v2.4）：未过可核性门 → annotationRequired；过门 → 不要求。 判定在生产侧做完，成文线只执行不重判——与
   * presentation 同机制。
   */
  @Test
  void carriesProvenanceForUnbackedAnchors() {
    ReportDataPackage pkg =
        evaluator.evaluate(List.of(ERGONOMICS), INPUT, ArtifactEntitlement.FREE, EVALUATED_ON);

    AnchorProvenance draft = anchor(pkg, "lkp-counter-height").provenance();
    assertTrue(draft.annotationRequired());
    assertEquals("draft", draft.calibration());
    assertEquals("测试源", draft.source());
    assertFalse(anchor(pkg, "lkp-passage-main").provenance().annotationRequired());
    assertEquals(EVALUATED_ON, pkg.evaluatedOn());
  }

  /**
   * 未过门的定位数字随页挂现场复核话术（规则 4.10c 配套，v2.4 新增）：v2.4 之前它一律隐藏，取消隐藏后 风险由"同页标注 +
   * 现场复核话术"共同承接——只标一句"这条没依据"，等于没告诉业主该怎么办。
   */
  @Test
  void derivesSiteCheckLockedTextForUnbackedLocatingNumber() {
    ReportDataPackage pkg =
        evaluator.evaluate(
            List.of(locatingSnapshot()), INPUT, ArtifactEntitlement.FREE, EVALUATED_ON);

    assertEquals(Map.of("ergonomics", List.of("GUIDE_SITE_CHECK")), pkg.lockedTextsByDomain());
  }

  /** 过门的定位数字不触发派生必挂：现场复核话术挂的是"没依据"，不是"是定位数字"。 */
  @Test
  void skipsSiteCheckLockedTextWhenLocatingNumberIsCalibrated() {
    ReportDataPackage pkg =
        evaluator.evaluate(
            List.of(locatingSnapshot("calibrated")), INPUT, ArtifactEntitlement.FREE, EVALUATED_ON);

    assertEquals(Map.of(), pkg.lockedTextsByDomain());
  }

  @Test
  void isReplayableAndOrderIndependent() {
    ReleaseSnapshot lighting =
        new ReleaseSnapshot(
            "lighting",
            "lighting@v1",
            List.of(param("lkp-cct-living", "起居色温", Map.of("v", 3000), null, "draft")),
            List.of(),
            List.of(),
            List.of());

    ReportDataPackage a =
        evaluator.evaluate(
            List.of(ERGONOMICS, lighting), INPUT, ArtifactEntitlement.FREE, EVALUATED_ON);
    ReportDataPackage b =
        evaluator.evaluate(
            List.of(lighting, ERGONOMICS), INPUT, ArtifactEntitlement.FREE, EVALUATED_ON);

    assertEquals(a, b);
    assertEquals(List.of("ergonomics", "lighting"), a.domains());
  }

  @Test
  void carriesSelfContainedCompositionPayload() {
    ReportDataPackage pkg =
        evaluator.evaluate(List.of(ERGONOMICS), INPUT, ArtifactEntitlement.FREE, EVALUATED_ON);

    assertEquals(List.of(PERSONA), pkg.personasByDomain().get("ergonomics"));
    // 判官层判据同样随包（自包含）：反例样例与 status 一并下发，成文线才拼得出判官 prompt
    assertEquals(List.of(JUDGE_CHECK, CHECK), pkg.checksByDomain().get("ergonomics"));
    assertEquals("你和你太太", pkg.checksByDomain().get("ergonomics").get(0).examples().get(0).bad());
    assertEquals("observing", pkg.checksByDomain().get("ergonomics").get(0).status());
    assertEquals(List.of("依据", "综合考量"), pkg.bannedTermsByDomain().get("ergonomics"));
  }

  private static ReportAnchor anchor(ReportDataPackage pkg, String lkpId) {
    return pkg.anchors().stream().filter(x -> x.lkpId().equals(lkpId)).findFirst().orElseThrow();
  }

  private static GapRecord gap(ReportDataPackage pkg, String lkpId) {
    return pkg.gaps().stream().filter(x -> x.lkpId().equals(lkpId)).findFirst().orElseThrow();
  }
}
