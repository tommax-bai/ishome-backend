package com.ishome.project.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ishome.project.domain.rulebook.AnchorPresentation;
import com.ishome.project.domain.rulebook.AnchorProvenance;
import com.ishome.project.domain.rulebook.ArtifactEntitlement;
import com.ishome.project.domain.rulebook.AttributeAsset;
import com.ishome.project.domain.rulebook.CheckAsset;
import com.ishome.project.domain.rulebook.CheckExample;
import com.ishome.project.domain.rulebook.EvaluationInput;
import com.ishome.project.domain.rulebook.GapRecord;
import com.ishome.project.domain.rulebook.ParameterAsset;
import com.ishome.project.domain.rulebook.PersonaAsset;
import com.ishome.project.domain.rulebook.ReleaseSnapshot;
import com.ishome.project.domain.rulebook.ReportAnchor;
import com.ishome.project.domain.rulebook.ReportDataPackage;
import com.ishome.project.domain.rulebook.RuleAsset;
import com.ishome.project.domain.rulebook.RulebookEvaluator;
import com.ishome.project.domain.rulebook.TriggeredRule;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 求值纯函数：三条求值路径、降档门禁、规则触发判定、可重放与顺序无关性（规则 8.2/4.10、图 v0.2 §0）。 */
class RulebookEvaluatorTest {

  private final RulebookEvaluator evaluator = new RulebookEvaluator();

  /** 求值基准日固定：它是入参不是时钟，测试里更不能取当天——取当天等于让断言随日历漂移。 */
  private static final LocalDate EVALUATED_ON = LocalDate.of(2026, 8, 29);

  /** {@code valueKind} 显式入参：两层模型里类别是**声明**不是推断（规则 1.9），夹具照实写。 */
  private static ParameterAsset param(
      String id, String name, String valueKind, Object value, String formula, String calibration) {
    return new ParameterAsset(
        id, name, "analysis", valueKind, value, null, formula, "mm", calibration, "测试源", 1);
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

  /** rule 夹具：{@code trigger} 与真种子逐字同形（快照 jsonb 原样，见 rulebook-seeds 各域 rules.yaml）。 */
  private static RuleAsset rule(String assetId, String content, Map<String, Object> trigger) {
    return new RuleAsset(
        assetId, "tier-practice", content, "为什么这么做", "recommended", "draft", trigger, List.of());
  }

  /** 户型特征触发（真种子 {@code ergonomics/rules.yaml}）：厨房 U 形时两排间距取上限。 */
  private static final RuleAsset DUAL_COOK_RULE =
      rule(
          "rule-practice-ergo-dual-cook-width",
          "两人同时下厨时，U型两排间距取上限区间",
          Map.of("type", "layout_feature", "layout_feature", "kitchen_u_shape"));

  /** 问卷答案触发（真种子）：首版无执行器，一律按未触发处理——扩展事件见 RuleTriggerPolicy 的类注释。 */
  private static final RuleAsset ELDER_GRAB_BAR_RULE =
      new RuleAsset(
          "rule-personal-ergo-elder-grab-bar",
          "tier-personal",
          "卫生间马桶侧与淋浴区预埋扶手基层",
          "老人起身借力点",
          "recommended",
          "draft",
          Map.of(
              "type", "answer", "question_id", "Q-FAMILY", "answer_match", List.of("elder_living")),
          List.of("art-hydro-checklist"));

  private static final ReleaseSnapshot ERGONOMICS =
      new ReleaseSnapshot(
          "ergonomics",
          "ergonomics@v1",
          List.of(
              param("lkp-counter-height", "橱柜台面高", "range", null, "主厨身高/2 + [50,100]", "draft"),
              param("lkp-wardrobe-rod", "衣柜挂杆高", "single", null, "身高 × 1.2", "draft"),
              param(
                  "lkp-passage-main",
                  "主通道净宽",
                  "range",
                  Map.of("min", 900),
                  "calibrated",
                  "calibrated"),
              param("lkp-tv-distance", "电视观看距离", "range", null, "屏高 × [3,4]", "draft"),
              // valueKind 缺席的公式落点（真库同款＝lkp-budget-driver）：求不出，也就不产出落点
              param("lkp-mystery", "无可执行形态", null, null, "神秘公式", "draft"),
              param("lkp-empty", "空定义", null, null, null, "draft")),
          List.of(),
          List.of(ELDER_GRAB_BAR_RULE, DUAL_COOK_RULE),
          List.of(PERSONA),
          List.of(CHECK, JUDGE_CHECK),
          List.of("依据", "综合考量"),
          Map.of("methodology", List.of("依据", "综合考量")));

  /**
   * 匿名画像的户型特征标记集：**键＝闭集内的标记名**（contracts {@code rulebook/layout_features.json}）、
   * **值＝这条标记成立的依据**（人话）。原夹具写的是 {@code kitchen_shape: "U"}——那是"键=值再投影"的形态， 契约明文禁止（同概念两套名 +
   * 一张会漂移的映射表，规则 1.8 第四条）。
   */
  private static final EvaluationInput INPUT =
      new EvaluationInput(
          1700, 1780, null, null, Map.of("kitchen_u_shape", "厨房三面台面围合，中间通道贯通"), null, null, null);

  @Test
  void evaluatesFormulaAndPassThroughAnchors() {
    ReportDataPackage pkg =
        evaluator.evaluate(List.of(ERGONOMICS), INPUT, ArtifactEntitlement.FREE, EVALUATED_ON);

    ReportAnchor counter = anchor(pkg, "lkp-counter-height");
    assertEquals(Map.of("min", 900, "max", 950), counter.value());
    assertTrue(counter.degraded());
    assertEquals("ergonomics@v1", counter.basisTag());

    // 公式求出的单值是**标量**：v 壳去掉后 {lkp-x.v} 这种引用连写都写不出来（规则 1.9 一）
    assertEquals(2136L, anchor(pkg, "lkp-wardrobe-rod").value());
    assertEquals("single", anchor(pkg, "lkp-wardrobe-rod").valueKind());

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
                "range",
                Map.of("min", 300, "max", 350),
                null,
                null,
                "mm",
                calibration,
                "行业通行",
                1)),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        Map.of());
  }

  /**
   * 全屋收纳总长 = 套内面积 × 收纳密度基准——**报告里第一条真的"量"**（2026-08-31）。
   *
   * <p>立案：真跑实测造价章有五条 calibrated 单价却算不出任何总价，收纳章说不出全屋要多少米收纳
   * ——缺的从来不是单价，是量。而这一条的量，靠业主自己知道的两个数就够了，不必等定稿平面。
   *
   * <p>密度基准取**同一份快照内**的 {@code lkp-storage-density-baseline}：不跨域取值，否则就在求值线 内部造出章与章的依赖。
   */
  @Test
  void computesStorageTotalMetersFromNetAreaAndDensity() {
    EvaluationInput withArea =
        new EvaluationInput(1700, 1780, null, null, Map.of(), null, 110.0, 80);

    ReportDataPackage pkg =
        evaluator.evaluate(
            List.of(storageQuantitySnapshot()), withArea, ArtifactEntitlement.PAID, EVALUATED_ON);

    // 套内 = 110 × 80% = 88 ㎡；88 × [0.25, 0.35] = [22.0, 30.8]
    ReportAnchor total = anchor(pkg, "lkp-storage-total-meters");
    assertEquals(Map.of("min", 22.0, "max", 30.8), total.value());
    assertEquals("米", total.unit());
  }

  /**
   * 公式算出来的数**必须带上它自己的推导**——不带，写作步就会编一个。
   *
   * <p>立案（2026-08-31 真跑）：收纳总长第一次算出来并写进正文（22.0–30.8 米），紧跟着一句编的
   * 解释「这个范围锚定的是当前囤货节奏与墙面可嵌入家具形态的交集」。查下去，这条落点 source 是
   * null。**数字是真的、解释是假的，比两个都假更危险**：读者会因为数字可信而连解释一起信。
   */
  @Test
  void computedAnchorCarriesItsOwnDerivation() {
    ReportDataPackage pkg =
        evaluator.evaluate(
            List.of(storageQuantitySnapshot()),
            new EvaluationInput(1700, 1780, null, null, Map.of(), null, 110.0, 80),
            ArtifactEntitlement.PAID,
            EVALUATED_ON);

    ReportAnchor total = anchor(pkg, "lkp-storage-total-meters");
    assertNotNull(total.source(), "算出来的数没有说明它是怎么来的——写作步只能编");
    assertTrue(total.source().contains("套内面积"), "推导要说清用了哪个输入");
    assertTrue(total.source().contains("收纳密度基准"), "推导要说清乘了哪条系数");
  }

  /** 缺面积就**如实记 gap-**，不猜——而且 reason 要是 missing_input（公式有实现、是输入没给）。 */
  @Test
  void storageTotalMetersWithoutAreaIsAnHonestGap() {
    ReportDataPackage pkg =
        evaluator.evaluate(
            List.of(storageQuantitySnapshot()),
            new EvaluationInput(1700, 1780, null, null, Map.of(), null, null, null),
            ArtifactEntitlement.PAID,
            EVALUATED_ON);

    GapRecord gap =
        pkg.gaps().stream()
            .filter(g -> "lkp-storage-total-meters".equals(g.lkpId()))
            .findFirst()
            .orElseThrow();
    assertEquals("missing_input", gap.reason());
    assertEquals("storage@v1", gap.basisTag());
  }

  /** 量的夹具：密度基准有值、总长只有公式——两条合起来才算得出这一户的收纳总长。 */
  private static ReleaseSnapshot storageQuantitySnapshot() {
    return new ReleaseSnapshot(
        "storage",
        "storage@v1",
        List.of(
            new ParameterAsset(
                "lkp-storage-density-baseline",
                "收纳长度密度基准",
                "analysis",
                "range",
                Map.of("min", 0.25, "max", 0.35),
                null,
                null,
                "米/㎡",
                "draft",
                "内部经验",
                1),
            new ParameterAsset(
                "lkp-storage-total-meters",
                "全屋收纳总长",
                "analysis",
                "range",
                null,
                null,
                "套内面积 × 收纳密度基准",
                "米",
                "draft",
                "内部经验",
                1)),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        Map.of());
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

  /**
   * 必挂集是**并集**不是覆盖（两条线接通落地）：求值线派生的那半（未过门定位数字 → 现场复核话术）与调用方 按 art-
   * 传入的那半（产物自己的必挂列，如造价章免责）各自成立、理由不同，任一侧漏挂都是纪律失效。
   */
  @Test
  void unionsCallerSuppliedLockedTextsWithDerivedOnes() {
    ReportDataPackage pkg =
        evaluator.evaluate(
            List.of(locatingSnapshot()),
            INPUT,
            ArtifactEntitlement.PAID,
            EVALUATED_ON,
            Map.of(
                "ergonomics", List.of("DISCLAIM_APPENDIX"), "budget", List.of("DISCLAIM_PRICE")));

    assertEquals(
        Map.of(
            "ergonomics",
            List.of("DISCLAIM_APPENDIX", "GUIDE_SITE_CHECK"),
            "budget",
            List.of("DISCLAIM_PRICE")),
        pkg.lockedTextsByDomain());
  }

  /** 并集去重且排序：同一条 ID 两侧都要求时只出现一次，域内顺序不随入参迭代序漂移（规则 8.2 字节级可重放）。 */
  @Test
  void dedupesAndSortsLockedTextsSoOutputStaysReplayable() {
    ReportDataPackage pkg =
        evaluator.evaluate(
            List.of(locatingSnapshot()),
            INPUT,
            ArtifactEntitlement.PAID,
            EVALUATED_ON,
            Map.of(
                "ergonomics", List.of("GUIDE_SITE_CHECK", "DISCLAIM_RENDER", "DISCLAIM_APPENDIX")));

    assertEquals(
        List.of("DISCLAIM_APPENDIX", "DISCLAIM_RENDER", "GUIDE_SITE_CHECK"),
        pkg.lockedTextsByDomain().get("ergonomics"));
  }

  // ── 两层模型：七类 valueKind 的产出形态（规则 1.9，规范 v2.8） ───────────────────────

  /** 带参考平面的落点夹具：单位与参考平面都是**元信息**，各有各的字段，不进 value（规则 1.9 二）。 */
  private static ParameterAsset illuminance() {
    return new ParameterAsset(
        "lkp-illuminance-living",
        "起居室照度标准值",
        "analysis",
        "scenario",
        Map.of("general", 100, "reading", 300),
        "0.75m 水平面",
        null,
        "lx",
        "calibrated",
        "GB/T 50034-2024 表5.2.1",
        1);
  }

  private static ReleaseSnapshot kindsSnapshot() {
    return new ReleaseSnapshot(
        "lighting",
        "lighting@v9",
        List.of(
            param("lkp-cct-living", "起居与卧室色温", "single", 3000, null, "draft"),
            param("lkp-bed-height", "床面高", "range", Map.of("min", 450, "max", 500), null, "draft"),
            illuminance(),
            param(
                "lkp-budget-confidence-width",
                "置信到区间宽度的映射",
                "tier",
                Map.of("high", 0.15, "medium", 0.30, "low", 0.50),
                null,
                "draft"),
            param(
                "lkp-shower-clear",
                "淋浴房内空",
                "dimension",
                Map.of("depth", Map.of("min", 800), "width", Map.of("min", 800)),
                null,
                "draft"),
            param(
                "lkp-budget-share",
                "分项造价占比带",
                "component",
                Map.of(
                    "main-material", Map.of("min", 0.20, "max", 0.35),
                    "demolition", Map.of("min", 0.03, "max", 0.08)),
                null,
                "draft"),
            param(
                "lkp-material-tier-gap",
                "三档替代价差带",
                "comparison",
                Map.of("high-vs-medium", Map.of("min", 1.5, "max", 2.5)),
                null,
                "draft")),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        Map.of());
  }

  /**
   * 七类各自的产出形态：类别**随落点下发**，成文线据此分支、不靠推断键名（规则 1.9 一）。
   *
   * <p>`single` 是标量、`range` 是 {@code {min,max}}、其余五类是 项名 → 标量|区间——三种形态而不是一种，
   * 正是"正文能引用其中一项"的结构前提：整条落点只能整条引用时，"沙发旁读书那块要单独加亮"这句话没有 合法写法，模型只能自造占位符（六轮 0/6 的立案材料，规范 §14.13）。
   */
  @Test
  void carriesEveryValueKindInItsOwnShape() {
    ReportDataPackage pkg =
        evaluator.evaluate(List.of(kindsSnapshot()), INPUT, ArtifactEntitlement.PAID, EVALUATED_ON);

    assertEquals("single", anchor(pkg, "lkp-cct-living").valueKind());
    assertEquals(3000, anchor(pkg, "lkp-cct-living").value());

    assertEquals("range", anchor(pkg, "lkp-bed-height").valueKind());
    assertEquals(Map.of("min", 450, "max", 500), anchor(pkg, "lkp-bed-height").value());

    assertEquals("scenario", anchor(pkg, "lkp-illuminance-living").valueKind());
    assertEquals(
        Map.of("general", 100, "reading", 300), anchor(pkg, "lkp-illuminance-living").value());

    assertEquals("tier", anchor(pkg, "lkp-budget-confidence-width").valueKind());
    assertEquals(
        Map.of("high", 0.15, "medium", 0.30, "low", 0.50),
        anchor(pkg, "lkp-budget-confidence-width").value());

    assertEquals("dimension", anchor(pkg, "lkp-shower-clear").valueKind());
    assertEquals(
        Map.of("depth", Map.of("min", 800), "width", Map.of("min", 800)),
        anchor(pkg, "lkp-shower-clear").value());

    assertEquals("component", anchor(pkg, "lkp-budget-share").valueKind());
    assertEquals(
        Map.of(
            "main-material", Map.of("min", 0.20, "max", 0.35),
            "demolition", Map.of("min", 0.03, "max", 0.08)),
        anchor(pkg, "lkp-budget-share").value());

    assertEquals("comparison", anchor(pkg, "lkp-material-tier-gap").valueKind());
    assertEquals(
        Map.of("high-vs-medium", Map.of("min", 1.5, "max", 2.5)),
        anchor(pkg, "lkp-material-tier-gap").value());
  }

  /**
   * 元信息不与项同层（规则 1.9 二）：单位在 {@code unit}、参考平面在 {@code referencePlane}，{@code value} 里只有项。
   *
   * <p>理由不是整洁：只要它们与项同层，{@code {lkp-x.unit}}（引用出一个单位字符串）就是语法上合法的 写法，"别那么写"这种约定管不住——所以让它写不出来。
   */
  @Test
  void keepsMetadataOutOfValue() {
    ReportDataPackage pkg =
        evaluator.evaluate(List.of(kindsSnapshot()), INPUT, ArtifactEntitlement.PAID, EVALUATED_ON);

    ReportAnchor illuminance = anchor(pkg, "lkp-illuminance-living");
    assertEquals("lx", illuminance.unit());
    assertEquals("0.75m 水平面", illuminance.referencePlane());
    assertEquals(Set.of("general", "reading"), ((Map<?, ?>) illuminance.value()).keySet());
    for (ReportAnchor each : pkg.anchors()) {
      if (each.value() instanceof Map<?, ?> value) {
        assertFalse(value.containsKey("unit"), each.lkpId() + " 的 value 里混进了 unit");
        assertFalse(value.containsKey("plane"), each.lkpId() + " 的 value 里混进了 plane");
      }
    }
  }

  /**
   * 老 release 快照没有 value_kind 列时按**实际形态**兜底：标量 → single、{@code {min,max}} → range。
   *
   * <p>兜底不是推断的翻案：项名映射一律给 {@code null}——scenario 与 component 的 value 形态一模一样，
   * 差别只在项名走哪份受控词表，从值的形状推不出来。猜一个填进去，成文线就会按错误的词表校项名。
   */
  @Test
  void fallsBackToActualShapeWhenSnapshotPredatesValueKind() {
    ReleaseSnapshot legacy =
        new ReleaseSnapshot(
            "lighting",
            "lighting@v1",
            List.of(
                param("lkp-cct-living", "起居色温", null, 3000, null, "draft"),
                param("lkp-bed-height", "床面高", null, Map.of("min", 450, "max", 500), null, "draft"),
                param("lkp-color-ratio", "配色比例", null, Map.of("main", 0.6), null, "draft")),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of());

    ReportDataPackage pkg =
        evaluator.evaluate(List.of(legacy), INPUT, ArtifactEntitlement.PAID, EVALUATED_ON);

    assertEquals("single", anchor(pkg, "lkp-cct-living").valueKind());
    assertEquals("range", anchor(pkg, "lkp-bed-height").valueKind());
    assertNull(anchor(pkg, "lkp-color-ratio").valueKind());
  }

  /** 过门的定位数字不触发派生必挂：现场复核话术挂的是"没依据"，不是"是定位数字"。 */
  @Test
  void skipsSiteCheckLockedTextWhenLocatingNumberIsCalibrated() {
    ReportDataPackage pkg =
        evaluator.evaluate(
            List.of(locatingSnapshot("calibrated")), INPUT, ArtifactEntitlement.FREE, EVALUATED_ON);

    assertEquals(Map.of(), pkg.lockedTextsByDomain());
  }

  // ── 造价章：work_item 单价资产 → 落点投影（规则 5.15） ───────────────────────────────

  /** 单价资产夹具：真库形态（区间 + 城市档细分 + 时效两列），只改本用例关心的那一项。 */
  private static AttributeAsset price(
      String assetId,
      Map<String, Object> props,
      String calibration,
      LocalDate effectiveFrom,
      LocalDate effectiveTo) {
    return new AttributeAsset(
        assetId,
        "水电改造人工费",
        "work_item",
        props,
        effectiveFrom,
        effectiveTo,
        calibration,
        "图纸之家 tuzhizhijia.com/fangchan/8468",
        1);
  }

  private static final Map<String, Object> HYDRO_PROPS =
      Map.of(
          "unit",
          "㎡",
          "price_range",
          List.of(25, 68),
          "breakdown",
          Map.of("一线", List.of(60, 68), "三四线", List.of(25, 50)));

  private static ReleaseSnapshot budgetSnapshot(AttributeAsset... attributes) {
    return new ReleaseSnapshot(
        "budget",
        "budget@v7",
        List.of(),
        List.of(attributes),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        Map.of());
  }

  private static EvaluationInput inputWithCityTier(String cityTier) {
    return new EvaluationInput(1700, 1780, null, null, Map.of(), cityTier, null, null);
  }

  /**
   * 造价章的数字来自单价库不是参数表：work_item 资产投影为落点，量纲是**元每计价单位**。
   *
   * <p>单位只给"㎡"会被写作器读成面积——落点单位是"量纲入名"（开发规范 §4.1）的数据侧同款。
   */
  @Test
  void projectsWorkItemPriceIntoAnchorWithMoneyUnit() {
    ReportDataPackage pkg =
        evaluator.evaluate(
            List.of(
                budgetSnapshot(
                    price(
                        "attr-price-hydro-labor-sqm",
                        HYDRO_PROPS,
                        "calibrated",
                        LocalDate.of(2026, 8, 28),
                        LocalDate.of(2026, 11, 28)))),
            inputWithCityTier(null),
            ArtifactEntitlement.PAID,
            EVALUATED_ON);

    ReportAnchor anchor = anchor(pkg, "lkp-price-hydro-labor-sqm");
    assertEquals("元/㎡", anchor.unit());
    assertEquals(Map.of("min", 25, "max", 68), anchor.value());
    assertEquals("analysis", anchor.numberClass());
    assertEquals("budget@v7", anchor.basisTag());
    assertEquals(AnchorPresentation.THESIS_SUPPORT, anchor.presentation());
    assertEquals(LocalDate.of(2026, 8, 28), anchor.provenance().effectiveFrom());
    assertFalse(anchor.provenance().annotationRequired());
  }

  /** 城市档逐字命中 breakdown 即取该档——档名是数据自带的词面，不经任何映射表（裁决 2026-08-29）。 */
  @Test
  void picksCityTierBandFromBreakdown() {
    ReportDataPackage pkg =
        evaluator.evaluate(
            List.of(
                budgetSnapshot(
                    price("attr-price-hydro-labor-sqm", HYDRO_PROPS, "calibrated", null, null))),
            inputWithCityTier("一线"),
            ArtifactEntitlement.PAID,
            EVALUATED_ON);

    assertEquals(Map.of("min", 60, "max", 68), anchor(pkg, "lkp-price-hydro-labor-sqm").value());
  }

  /** 命不中是常态不是异常：细分按墙体类型/档位时，全国粗档区间就是这条单价的正确答案。 */
  @Test
  void fallsBackToNationwideRangeWhenCityTierMisses() {
    Map<String, Object> byWallType =
        Map.of(
            "unit", "㎡",
            "price_range", List.of(20, 60),
            "breakdown", Map.of("普通墙", List.of(20, 30), "混凝土墙", List.of(40, 60)));
    ReportDataPackage pkg =
        evaluator.evaluate(
            List.of(
                budgetSnapshot(
                    price("attr-price-demolition", byWallType, "calibrated", null, null))),
            inputWithCityTier("一线"),
            ArtifactEntitlement.PAID,
            EVALUATED_ON);

    assertEquals(Map.of("min", 20, "max", 60), anchor(pkg, "lkp-price-demolition").value());
  }

  /**
   * 过期单价**照常出金额**，随标注取数时间与来源（规则 5.15，v2.4 裁决 2026-08-29）。
   *
   * <p>原口径"越界即降档为仅出结构占比、不出金额"已作废：过期的行情仍是当时的真实行情，标了时间业主
   * 自己会折算，抹掉金额反而少给他一个判断维度。这条用例是那次推翻的回归锚——真库当前无过期单价， 只有它守着这条纪律。
   */
  @Test
  void staleUnitPriceStillDeliversMoneyWithAcquisitionDate() {
    ReportDataPackage pkg =
        evaluator.evaluate(
            List.of(
                budgetSnapshot(
                    price(
                        "attr-price-wall-paint",
                        Map.of("unit", "㎡", "price_range", List.of(10, 20)),
                        "calibrated",
                        LocalDate.of(2025, 8, 1),
                        LocalDate.of(2026, 8, 1)))),
            inputWithCityTier("一线"),
            ArtifactEntitlement.PAID,
            EVALUATED_ON);

    ReportAnchor anchor = anchor(pkg, "lkp-price-wall-paint");
    assertEquals(Map.of("min", 10, "max", 20), anchor.value());
    assertTrue(anchor.provenance().annotationRequired());
    assertEquals(LocalDate.of(2025, 8, 1), anchor.provenance().effectiveFrom());
    assertEquals(LocalDate.of(2026, 8, 1), anchor.provenance().effectiveTo());
    // 过期不降语域：可核性门与时效是两件事（前者管能不能作支点，后者管标不标取数时间）
    assertEquals(AnchorPresentation.THESIS_SUPPORT, anchor.presentation());
    assertFalse(anchor.degraded());
  }

  /** 只投影 work_item：描述性属性（材质卡/色板/收纳物品）没有值形态，投影它们只会造出一批空落点。 */
  @Test
  void skipsAttributesOtherThanWorkItem() {
    AttributeAsset material =
        new AttributeAsset(
            "attr-material-sintered-stone",
            "哑光岩板",
            "material",
            Map.of("wear", "high"),
            null,
            null,
            "draft",
            "厂商公开参数",
            1);
    AttributeAsset priceless =
        new AttributeAsset(
            "attr-price-broken",
            "无区间单价",
            "work_item",
            Map.of("unit", "㎡"),
            null,
            null,
            "draft",
            null,
            1);

    ReportDataPackage pkg =
        evaluator.evaluate(
            List.of(budgetSnapshot(material, priceless)),
            inputWithCityTier("一线"),
            ArtifactEntitlement.PAID,
            EVALUATED_ON);

    assertEquals(List.of(), pkg.anchors());
    // 求不出的单价走 gap-（回流不阻塞），不是静默消失
    assertEquals(List.of("lkp-price-broken"), pkg.gaps().stream().map(GapRecord::lkpId).toList());
    assertEquals("empty_definition", gap(pkg, "lkp-price-broken").reason());
  }

  @Test
  void isReplayableAndOrderIndependent() {
    ReleaseSnapshot lighting =
        new ReleaseSnapshot(
            "lighting",
            "lighting@v1",
            List.of(param("lkp-cct-living", "起居色温", "single", 3000, null, "draft")),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of());

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
    // 禁词分组随包（2026-08-30）：平表照旧、分组另给一份——打回话要按"为什么禁"分句，
    // 一句"换人话说"对行话成立、对软话是错的指令（软话不是换个近义词能救的）。
    assertEquals(
        Map.of("methodology", List.of("依据", "综合考量")),
        pkg.bannedTermGroupsByDomain().get("ergonomics"));
  }

  // ── 规则触发判定（规范 §4.1 三层三触发；关系与数字同族，都不由 LLM 决定） ─────────────

  /** 收纳域真种子形态：无条件触发一条、户型特征触发一条、问卷答案触发一条（后者首版无执行器）。 */
  private static ReleaseSnapshot storageSnapshot() {
    return new ReleaseSnapshot(
        "storage",
        "storage@v7",
        List.of(),
        List.of(),
        List.of(
            rule("rule-practice-storage-entry-parcel", "玄关设快递拆包位（台面或翻板）", Map.of("type", "always")),
            rule(
                "rule-practice-storage-balcony-cleaning",
                "阳台留清洁工具位（含插座）",
                Map.of("type", "layout_feature", "layout_feature", "balcony_service")),
            rule(
                "rule-personal-storage-bulk-buying",
                "增设囤货仓（阳台或次卧柜下段，承重层板）",
                Map.of("type", "answer", "question_id", "Q-STORAGE"))),
        List.of(),
        List.of(),
        List.of(),
        Map.of());
  }

  private static EvaluationInput inputWithFeatures(Map<String, String> layoutFeatures) {
    return new EvaluationInput(1700, 1780, null, null, layoutFeatures, null, null, null);
  }

  /**
   * 户型特征命中：**键存在即触发，值不参与匹配**；值进 {@code triggeredBy.evidence} 逐字留痕—— 报告里"因为你家阳台带家政位"的数据来源（规则 4.3
   * 可追溯性的户型侧对应物）。
   */
  @Test
  void triggersLayoutFeatureRuleOnKeyPresenceAndCarriesTheValueAsEvidence() {
    ReportDataPackage pkg =
        evaluator.evaluate(
            List.of(storageSnapshot()),
            inputWithFeatures(Map.of("balcony_service", "阳台内有洗衣机设备位")),
            ArtifactEntitlement.PAID,
            EVALUATED_ON);

    TriggeredRule balcony = triggered(pkg, "storage", "rule-practice-storage-balcony-cleaning");
    assertEquals("layout_feature", balcony.triggeredBy().type());
    assertEquals("balcony_service", balcony.triggeredBy().feature());
    assertEquals("阳台内有洗衣机设备位", balcony.triggeredBy().evidence());
    assertEquals("阳台留清洁工具位（含插座）", balcony.content());
    assertEquals("tier-practice", balcony.layer());
    assertEquals("draft", balcony.calibration());
  }

  /** 标记不在画像里就是不触发——没有近似匹配、没有映射表（映射表一旦存在就会与数据漂移）。 */
  @Test
  void skipsLayoutFeatureRuleWhenTheMarkIsAbsent() {
    ReportDataPackage pkg =
        evaluator.evaluate(
            List.of(storageSnapshot()),
            inputWithFeatures(Map.of("west_facing", "客厅朝西")),
            ArtifactEntitlement.PAID,
            EVALUATED_ON);

    assertEquals(
        List.of("rule-practice-storage-entry-parcel"),
        pkg.triggeredRulesByDomain().get("storage").stream().map(TriggeredRule::assetId).toList());
  }

  /**
   * {@code always} 无条件成立：画像为空也照进包，依据为空——无条件的事没有"因为"，编一个就是伪因果。
   *
   * <p>顺带钉住首版射程：{@code answer} 类无执行器，一律按未触发处理（扩展事件见 RuleTriggerPolicy 的类注释）。
   */
  @Test
  void triggersAlwaysRuleWithoutEvidenceAndLeavesUnimplementedTypesUntriggered() {
    ReportDataPackage pkg =
        evaluator.evaluate(
            List.of(storageSnapshot()),
            inputWithFeatures(Map.of()),
            ArtifactEntitlement.PAID,
            EVALUATED_ON);

    List<TriggeredRule> storage = pkg.triggeredRulesByDomain().get("storage");
    assertEquals(List.of("rule-practice-storage-entry-parcel"), assetIds(storage));
    assertEquals("always", storage.get(0).triggeredBy().type());
    assertNull(storage.get(0).triggeredBy().feature());
    assertNull(storage.get(0).triggeredBy().evidence());
  }

  /** {@code layoutFeatures} 为 null（调用方没传该字段）视同空集：不触发任何特征规则，也不炸。 */
  @Test
  void treatsMissingLayoutFeaturesAsEmptySet() {
    ReportDataPackage pkg =
        evaluator.evaluate(
            List.of(storageSnapshot()),
            inputWithFeatures(null),
            ArtifactEntitlement.PAID,
            EVALUATED_ON);

    assertEquals(
        List.of("rule-practice-storage-entry-parcel"),
        assetIds(pkg.triggeredRulesByDomain().get("storage")));
  }

  /** 域键恒存在、条目按 assetId 排序：一条没触发的域给空列表——"评过了、结论是没有"与"根本没评"是两件事， 缺键会让消费侧把前者读成后者。排序即可重放（规则 8.2）。 */
  @Test
  void keysEveryEvaluatedDomainAndSortsTriggeredRules() {
    ReportDataPackage pkg =
        evaluator.evaluate(
            List.of(ERGONOMICS, storageSnapshot(), budgetSnapshot()),
            inputWithFeatures(Map.of("kitchen_u_shape", "厨房三面台面围合", "balcony_service", "阳台带洗衣位")),
            ArtifactEntitlement.PAID,
            EVALUATED_ON);

    assertEquals(
        List.of("rule-practice-ergo-dual-cook-width"),
        assetIds(pkg.triggeredRulesByDomain().get("ergonomics")));
    assertEquals(
        List.of("rule-practice-storage-balcony-cleaning", "rule-practice-storage-entry-parcel"),
        assetIds(pkg.triggeredRulesByDomain().get("storage")));
    // budget 快照里一条 rule 都没有：给空列表而不是缺键
    assertTrue(pkg.triggeredRulesByDomain().containsKey("budget"));
    assertEquals(List.of(), pkg.triggeredRulesByDomain().get("budget"));
  }

  /** 触发的条目**不带触发条件也不带 consumers**：前者给了成文线就会想重判一次，后者成文线不认识 art-。 */
  @Test
  void carriesTriggeredRulesIntoPackageWithoutTriggerConditionOrConsumers() throws Exception {
    ReportDataPackage pkg =
        evaluator.evaluate(
            List.of(storageSnapshot()),
            inputWithFeatures(Map.of("balcony_service", "阳台内有洗衣机设备位")),
            ArtifactEntitlement.PAID,
            EVALUATED_ON);
    ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
    JsonNode json = mapper.readTree(mapper.writeValueAsBytes(pkg)).path("triggeredRulesByDomain");

    JsonNode balcony = json.path("storage").path(0);
    assertEquals("rule-practice-storage-balcony-cleaning", balcony.path("assetId").asText());
    assertTrue(balcony.path("trigger").isMissingNode());
    assertTrue(balcony.path("consumers").isMissingNode());
    assertEquals("阳台内有洗衣机设备位", balcony.path("triggeredBy").path("evidence").asText());
  }

  private static List<String> assetIds(List<TriggeredRule> rules) {
    return rules.stream().map(TriggeredRule::assetId).toList();
  }

  private static TriggeredRule triggered(ReportDataPackage pkg, String domain, String assetId) {
    return pkg.triggeredRulesByDomain().get(domain).stream()
        .filter(x -> x.assetId().equals(assetId))
        .findFirst()
        .orElseThrow();
  }

  private static ReportAnchor anchor(ReportDataPackage pkg, String lkpId) {
    return pkg.anchors().stream().filter(x -> x.lkpId().equals(lkpId)).findFirst().orElseThrow();
  }

  private static GapRecord gap(ReportDataPackage pkg, String lkpId) {
    return pkg.gaps().stream().filter(x -> x.lkpId().equals(lkpId)).findFirst().orElseThrow();
  }
}
