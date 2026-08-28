package com.ishome.project.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ishome.project.domain.rulebook.AnchorPresentation;
import com.ishome.project.domain.rulebook.AnchorPresentationPolicy;
import com.ishome.project.domain.rulebook.ArtifactEntitlement;
import com.ishome.project.domain.rulebook.CheckAsset;
import com.ishome.project.domain.rulebook.EvaluationInput;
import com.ishome.project.domain.rulebook.GapRecord;
import com.ishome.project.domain.rulebook.ParameterAsset;
import com.ishome.project.domain.rulebook.PersonaAsset;
import com.ishome.project.domain.rulebook.ReleaseSnapshot;
import com.ishome.project.domain.rulebook.ReportAnchor;
import com.ishome.project.domain.rulebook.ReportDataPackage;
import com.ishome.project.domain.rulebook.RulebookEvaluator;
import com.ishome.project.domain.rulebook.WithheldAnchor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** lkp- 求值纯函数：三条求值路径、降档门禁、可重放与顺序无关性（规则 8.2/4.10、图 v0.2 §0）。 */
class RulebookEvaluatorTest {

  private final RulebookEvaluator evaluator = new RulebookEvaluator();

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
          List.of(CHECK),
          List.of("依据", "综合考量"));

  private static final EvaluationInput INPUT =
      new EvaluationInput(1700, 1780, null, null, Map.of("kitchen_shape", "U"));

  @Test
  void evaluatesFormulaAndPassThroughAnchors() {
    ReportDataPackage pkg =
        evaluator.evaluate(List.of(ERGONOMICS), INPUT, ArtifactEntitlement.FREE);

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
        evaluator.evaluate(List.of(ERGONOMICS), INPUT, ArtifactEntitlement.FREE);

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
        evaluator.evaluate(List.of(ERGONOMICS), INPUT, ArtifactEntitlement.FREE);

    assertEquals(ArtifactEntitlement.FREE, pkg.entitlement());
    assertEquals(List.of(), pkg.withheldAnchors());
    assertEquals(AnchorPresentation.THESIS_SUPPORT, anchor(pkg, "lkp-passage-main").presentation());
    assertEquals(
        AnchorPresentation.REFERENCE_ONLY, anchor(pkg, "lkp-counter-height").presentation());
    assertEquals(AnchorPresentation.REFERENCE_ONLY, anchor(pkg, "lkp-wardrobe-rod").presentation());
  }

  /** PAID 门禁（规则 4.10）：未背书的点值根本不下发——只在 withheldAnchors 留审计条，成文线无从引用； 未背书的区间降档为参考形态；过门条目不受影响。 */
  @Test
  void paidWithholdsUnbackedPointValuesAndDegradesRanges() {
    ReportDataPackage pkg =
        evaluator.evaluate(List.of(ERGONOMICS), INPUT, ArtifactEntitlement.PAID);

    assertEquals(ArtifactEntitlement.PAID, pkg.entitlement());
    assertEquals(
        List.of("lkp-counter-height", "lkp-passage-main"),
        pkg.anchors().stream().map(ReportAnchor::lkpId).toList());
    assertEquals(
        List.of(new WithheldAnchor("lkp-wardrobe-rod", "ergonomics@v1", "no_range_form")),
        pkg.withheldAnchors());
    assertEquals(
        AnchorPresentation.REFERENCE_ONLY, anchor(pkg, "lkp-counter-height").presentation());
    assertEquals(AnchorPresentation.THESIS_SUPPORT, anchor(pkg, "lkp-passage-main").presentation());
    // 隐藏不污染 gap- 回流信号：gap 仍是求不出的那三条（规则 4.5 两类信号各走各的回路）
    assertEquals(3, pkg.gaps().size());
  }

  /** 定位数字未过门即隐藏，与值是不是区间无关（规则 2.2/2.3：参考口吻的定位数字不存在）。 */
  @Test
  void paidWithholdsUnbackedLocatingNumbers() {
    ReleaseSnapshot layout =
        new ReleaseSnapshot(
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
                    "draft",
                    "行业通行",
                    1)),
            List.of(),
            List.of(),
            List.of());

    ReportDataPackage pkg = evaluator.evaluate(List.of(layout), INPUT, ArtifactEntitlement.PAID);

    assertEquals(List.of(), pkg.anchors());
    assertEquals(
        List.of(
            new WithheldAnchor(
                "lkp-socket-height",
                "ergonomics@v1",
                AnchorPresentationPolicy.WITHHOLD_REASON_LOCATING_NUMBER)),
        pkg.withheldAnchors());
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
        evaluator.evaluate(List.of(ERGONOMICS, lighting), INPUT, ArtifactEntitlement.FREE);
    ReportDataPackage b =
        evaluator.evaluate(List.of(lighting, ERGONOMICS), INPUT, ArtifactEntitlement.FREE);

    assertEquals(a, b);
    assertEquals(List.of("ergonomics", "lighting"), a.domains());
  }

  @Test
  void carriesSelfContainedCompositionPayload() {
    ReportDataPackage pkg =
        evaluator.evaluate(List.of(ERGONOMICS), INPUT, ArtifactEntitlement.FREE);

    assertEquals(List.of(PERSONA), pkg.personasByDomain().get("ergonomics"));
    assertEquals(List.of(CHECK), pkg.checksByDomain().get("ergonomics"));
    assertEquals(List.of("依据", "综合考量"), pkg.bannedTermsByDomain().get("ergonomics"));
  }

  private static ReportAnchor anchor(ReportDataPackage pkg, String lkpId) {
    return pkg.anchors().stream().filter(x -> x.lkpId().equals(lkpId)).findFirst().orElseThrow();
  }

  private static GapRecord gap(ReportDataPackage pkg, String lkpId) {
    return pkg.gaps().stream().filter(x -> x.lkpId().equals(lkpId)).findFirst().orElseThrow();
  }
}
