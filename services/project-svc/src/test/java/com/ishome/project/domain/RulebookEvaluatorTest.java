package com.ishome.project.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.junit.jupiter.api.Test;

/** 求值纯函数：三条求值路径、降档门禁、规则触发判定、可重放与顺序无关性（规则 8.2/4.10、图 v0.2 §0）。 */
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
              param("lkp-counter-height", "橱柜台面高", null, "主厨身高/2 + [50,100]", "draft"),
              param("lkp-wardrobe-rod", "衣柜挂杆高", null, "身高 × 1.2", "draft"),
              param("lkp-passage-main", "主通道净宽", Map.of("min", 900), "calibrated", "calibrated"),
              param("lkp-tv-distance", "电视观看距离", null, "屏高 × [3,4]", "draft"),
              param("lkp-mystery", "无可执行形态", null, "神秘公式", "draft"),
              param("lkp-empty", "空定义", null, null, "draft")),
          List.of(),
          List.of(ELDER_GRAB_BAR_RULE, DUAL_COOK_RULE),
          List.of(PERSONA),
          List.of(CHECK, JUDGE_CHECK),
          List.of("依据", "综合考量"));

  /**
   * 匿名画像的户型特征标记集：**键＝闭集内的标记名**（contracts {@code rulebook/layout_features.json}）、
   * **值＝这条标记成立的依据**（人话）。原夹具写的是 {@code kitchen_shape: "U"}——那是"键=值再投影"的形态， 契约明文禁止（同概念两套名 +
   * 一张会漂移的映射表，规则 1.8 第四条）。
   */
  private static final EvaluationInput INPUT =
      new EvaluationInput(
          1700, 1780, null, null, Map.of("kitchen_u_shape", "厨房三面台面围合，中间通道贯通"), null);

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
        List.of());
  }

  private static EvaluationInput inputWithCityTier(String cityTier) {
    return new EvaluationInput(1700, 1780, null, null, Map.of(), cityTier);
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
            List.of(param("lkp-cct-living", "起居色温", Map.of("v", 3000), null, "draft")),
            List.of(),
            List.of(),
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
        List.of());
  }

  private static EvaluationInput inputWithFeatures(Map<String, String> layoutFeatures) {
    return new EvaluationInput(1700, 1780, null, null, layoutFeatures, null);
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
