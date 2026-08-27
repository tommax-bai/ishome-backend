package com.ishome.project.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ishome.project.domain.rulebook.EvaluationInput;
import com.ishome.project.domain.rulebook.GapRecord;
import com.ishome.project.domain.rulebook.ParameterAsset;
import com.ishome.project.domain.rulebook.PersonaAssetRef;
import com.ishome.project.domain.rulebook.ReleaseSnapshot;
import com.ishome.project.domain.rulebook.ReportAnchor;
import com.ishome.project.domain.rulebook.ReportDataPackage;
import com.ishome.project.domain.rulebook.RulebookEvaluator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** lkp- 求值纯函数：三条求值路径、降档标记、可重放与顺序无关性（规则 8.2、图 v0.2 §0）。 */
class RulebookEvaluatorTest {

  private final RulebookEvaluator evaluator = new RulebookEvaluator();

  private static ParameterAsset param(
      String id, String name, Map<String, Object> value, String formula, String calibration) {
    return new ParameterAsset(id, name, "analysis", value, formula, "mm", calibration, "测试源", 1);
  }

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
          List.of(new PersonaAssetRef("persona-ergonomics", 1)));

  private static final EvaluationInput INPUT =
      new EvaluationInput(1700, 1780, null, null, Map.of("kitchen_shape", "U"));

  @Test
  void evaluatesFormulaAndPassThroughAnchors() {
    ReportDataPackage pkg = evaluator.evaluate(List.of(ERGONOMICS), INPUT);

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
    ReportDataPackage pkg = evaluator.evaluate(List.of(ERGONOMICS), INPUT);

    assertEquals(
        List.of("lkp-empty", "lkp-mystery", "lkp-tv-distance"),
        pkg.gaps().stream().map(GapRecord::lkpId).toList());
    assertEquals("empty_definition", gap(pkg, "lkp-empty").reason());
    assertEquals("formula_not_implemented", gap(pkg, "lkp-mystery").reason());
    assertEquals("missing_input", gap(pkg, "lkp-tv-distance").reason());
  }

  @Test
  void isReplayableAndOrderIndependent() {
    ReleaseSnapshot lighting =
        new ReleaseSnapshot(
            "lighting",
            "lighting@v1",
            List.of(param("lkp-cct-living", "起居色温", Map.of("v", 3000), null, "draft")),
            List.of(new PersonaAssetRef("persona-lighting", 1)));

    ReportDataPackage a = evaluator.evaluate(List.of(ERGONOMICS, lighting), INPUT);
    ReportDataPackage b = evaluator.evaluate(List.of(lighting, ERGONOMICS), INPUT);

    assertEquals(a, b);
    assertEquals(List.of("ergonomics", "lighting"), a.domains());
  }

  private static ReportAnchor anchor(ReportDataPackage pkg, String lkpId) {
    return pkg.anchors().stream().filter(x -> x.lkpId().equals(lkpId)).findFirst().orElseThrow();
  }

  private static GapRecord gap(ReportDataPackage pkg, String lkpId) {
    return pkg.gaps().stream().filter(x -> x.lkpId().equals(lkpId)).findFirst().orElseThrow();
  }
}
