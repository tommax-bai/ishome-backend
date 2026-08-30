package com.ishome.project;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.ulid.UlidCreator;
import com.ishome.project.application.ReportEvaluationAppService;
import com.ishome.project.domain.rulebook.AnchorPresentation;
import com.ishome.project.domain.rulebook.ArtifactEntitlement;
import com.ishome.project.domain.rulebook.CheckAsset;
import com.ishome.project.domain.rulebook.CheckExample;
import com.ishome.project.domain.rulebook.EvaluationInput;
import com.ishome.project.domain.rulebook.ReleaseNotFoundException;
import com.ishome.project.domain.rulebook.ReleaseRef;
import com.ishome.project.domain.rulebook.ReportAnchor;
import com.ishome.project.domain.rulebook.ReportDataPackage;
import com.ishome.project.domain.rulebook.TriggeredRule;
import com.ishome.project.testsupport.PostgresIntegrationTestSupport;
import com.ishome.shared.kernel.testsupport.EnabledIfLocalPostgres;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 求值线 PG 实跑（独立 schema svc_rulebook_it）：发布态快照 → ReleaseRepository 投影 → lkp- 求值 → 报告数据包。 快照夹具 =
 * publish_release.py 产出结构的裁剪版（lighting/ergonomics/budget 三域，图 v0.2 §8 首批）； 求值输入 = estate 标注户型特征 +
 * 匿名身高族（避开 floorplan-parse）。 验证图 v0.2 §8 首批第一件事：同一输入重复求值，序列化字节级同输出。
 */
@SpringBootTest
@EnabledIfLocalPostgres
@Import(PostgresIntegrationTestSupport.CleanMigrateConfig.class)
class RulebookEvaluationIntegrationTest {

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    PostgresIntegrationTestSupport.register(registry);
  }

  @Autowired ReportEvaluationAppService reportEvaluationAppService;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired ObjectMapper objectMapper;

  /** 求值基准日固定：基准日是入参不是时钟，测试里取当天等于让断言随日历漂移（规则 8.2 可重放）。 */
  private static final LocalDate EVALUATED_ON = LocalDate.of(2026, 8, 29);

  /**
   * 匿名身高族 + 户型特征标记集；tvScreenHeightMm 缺失 → gap 不阻塞。
   *
   * <p>特征**键＝闭集内的标记名**（contracts {@code rulebook/layout_features.json}）、**值＝该标记成立的依据** （人话，进 {@code
   * triggeredBy.evidence} 逐字留痕）。原夹具写的是 {@code kitchen_shape: "U"} 这类"键=值"形态，
   * 契约明文禁止——那是同概念两套名加一张会漂移的映射表（规则 1.8 第四条）。
   */
  private static final EvaluationInput INPUT =
      new EvaluationInput(
          1700,
          1780,
          1600,
          null,
          Map.of("kitchen_u_shape", "厨房三面台面围合，中间通道贯通", "balcony_service", "阳台内有洗衣机设备位"),
          "一线");

  @BeforeEach
  void seedReleases() {
    // Flyway clean 每个 Spring 上下文一次、@BeforeEach 每用例一次：先清夹具表避免 release_tag 唯一键冲突
    jdbcTemplate.update(
        "DELETE FROM " + PostgresIntegrationTestSupport.RULEBOOK_SCHEMA + ".releases");
    insertRelease(
        "lighting",
        "lighting@v1",
        """
        {"release_tag":"lighting@v1","domain":"lighting","assets":{"parameters":[
          {"asset_id":"lkp-illuminance-living","name":"起居室照度标准值","number_class":"analysis",
           "value":{"general":100,"reading":300,"unit":"lx"},"formula":null,"unit":"lx",
           "calibration":"calibrated","source":"GB 50034-2013 表5.2.1","version":1},
          {"asset_id":"lkp-cct-living","name":"起居与卧室色温","number_class":"selection",
           "value":{"v":3000},"formula":null,"unit":"K","calibration":"draft","source":"行业通行","version":1}],
          "personas":[{"asset_id":"persona-lighting","version":1}]}}
        """);
    insertRelease(
        "ergonomics",
        "ergonomics@v1",
        """
        {"release_tag":"ergonomics@v1","domain":"ergonomics","assets":{"rules":[
          {"asset_id":"rule-practice-ergo-dual-cook-width","domain":"ergonomics","layer":"tier-practice",
           "trigger":{"type":"layout_feature","layout_feature":"kitchen_u_shape"},
           "content":"两人同时下厨时，U型两排间距取上限区间","rationale":"一个人弯腰开柜、另一个人能过",
           "severity":"recommended","calibration":"draft","consumers":["art-ergonomics-chapter"],"version":1},
          {"asset_id":"rule-personal-ergo-elder-grab-bar","domain":"ergonomics","layer":"tier-personal",
           "trigger":{"type":"answer","question_id":"Q-FAMILY","answer_match":["elder_living"]},
           "content":"卫生间马桶侧与淋浴区预埋扶手基层","rationale":"老人起身借力点",
           "severity":"recommended","calibration":"draft","consumers":["art-hydro-checklist"],"version":1}],
          "parameters":[
          {"asset_id":"lkp-counter-height","name":"橱柜台面高","number_class":"selection",
           "value":null,"formula":"主厨身高/2 + [50,100]","unit":"mm","calibration":"draft","source":"行业通行","version":1},
          {"asset_id":"lkp-wardrobe-rod","name":"衣柜挂杆高","number_class":"selection",
           "value":null,"formula":"身高 × 1.2","unit":"mm","calibration":"draft","source":"行业通行","version":1},
          {"asset_id":"lkp-passage-main","name":"主通道净宽","number_class":"analysis",
           "value":{"min":900},"formula":null,"unit":"mm","calibration":"draft","source":"行业通行","version":1},
          {"asset_id":"lkp-tv-distance","name":"电视观看距离","number_class":"analysis",
           "value":null,"formula":"屏高 × [3,4]","unit":null,"calibration":"draft","source":"行业通行","version":1}],
          "personas":[{"asset_id":"persona-ergonomics","identity":"你在为这一家人校核尺寸。",
           "judgment_samples":[],"assertion_budget":[{"predicate":"通道净宽","requires":["lkp-passage-main"]}],
           "banned_terms":{"domain_extra":["人体工学"]},"version":1}],
          "checks":[{"asset_id":"cr-weak-words","check_type":"regex_deny","scope":["正文"],
           "pattern":"可能|建议考虑|也许","requirement":null,"message":"分析级结论禁弱词（规则 5.9）",
           "decided_by":"规范规则 5.9","threshold_refs":[],"examples":[],"status":"active","version":1},
           {"asset_id":"cr-fabricated-fact","check_type":"semantic_judge","scope":["正文"],
           "pattern":null,"requirement":"关于这家人的事实只能来自匿名画像","message":"编造输入之外的家庭事实",
           "decided_by":"规范规则 4.3 + 图 v0.2 §0","threshold_refs":[],
           "examples":[{"bad":"你和你太太","why":"画像里没有家庭构成信息","fixed":"两个人同时用的时候"},
                       {"bad":"三件不齐者","why":"","fixed":"投影时丢弃"}],
           "status":"observing","version":1}],
          "vocabularies":[{"asset_id":"vocab-banned-methodology","kind":"banned_term",
           "terms":{"methodology":["依据","综合考量"]},"version":1}]}}
        """);
    insertRelease(
        "budget",
        "budget@v1",
        """
        {"release_tag":"budget@v1","domain":"budget","assets":{"rules":[
          {"asset_id":"rule-practice-budget-hidden-item-warning","domain":"budget","layer":"tier-practice",
           "trigger":{"type":"always"},"content":"列常见低报高增项（联动 art-quotation-checklist）",
           "rationale":"签约时报低、施工中增项是最常见的博弈点","severity":"recommended",
           "calibration":"draft","consumers":["art-budget-chapter"],"version":1}],
          "parameters":[
          {"asset_id":"lkp-budget-confidence-width","name":"置信到区间宽度的映射","number_class":"analysis",
           "value":{"high":"±10%","medium":"±20%","low":"±35%"},"formula":null,"unit":null,
           "calibration":"draft","source":"内部规范 §5.9","version":1}],
          "attributes":[
          {"asset_id":"attr-price-hydro-labor-sqm","name":"水电改造人工费","entity_type":"work_item",
           "props":{"unit":"㎡","price_range":[25,68],"breakdown":{"一线":[60,68],"三四线":[25,50]}},
           "effective_from":"2026-08-28","effective_to":"2026-11-28",
           "calibration":"calibrated","source":"图纸之家 tuzhizhijia.com/fangchan/8468","version":1},
          {"asset_id":"attr-price-wall-paint","name":"墙面乳胶漆涂刷","entity_type":"work_item",
           "props":{"unit":"㎡","price_range":[10,20]},
           "effective_from":"2025-08-01","effective_to":"2026-08-01",
           "calibration":"calibrated","source":"尚美饰家 m.smsj.com/strategy/70811","version":1},
          {"asset_id":"attr-material-sintered-stone","name":"哑光岩板","entity_type":"material",
           "props":{"wear":"high"},"calibration":"draft","source":"厂商公开参数","version":1}],
          "personas":[{"asset_id":"persona-budget","version":1}]}}
        """);
  }

  private void insertRelease(String domain, String tag, String snapshot) {
    jdbcTemplate.update(
        "INSERT INTO "
            + PostgresIntegrationTestSupport.RULEBOOK_SCHEMA
            + ".releases (id, domain, release_tag, snapshot) VALUES (?, ?, ?, ?::jsonb)",
        UlidCreator.getUlid().toString(),
        domain,
        tag,
        snapshot);
  }

  /** 三域求值（FREE 侧全量下发）：公式代入、直取、降档标记、gap 记录、release 引用集齐全。 */
  @Test
  void evaluatesThreeDomainsAgainstPublishedReleases() {
    ReportDataPackage pkg =
        reportEvaluationAppService.evaluate(
            List.of("lighting", "ergonomics", "budget"),
            INPUT,
            ArtifactEntitlement.FREE,
            EVALUATED_ON);

    assertEquals(List.of("budget", "ergonomics", "lighting"), pkg.domains());
    assertEquals(
        List.of(
            new ReleaseRef("budget", "budget@v1"),
            new ReleaseRef("ergonomics", "ergonomics@v1"),
            new ReleaseRef("lighting", "lighting@v1")),
        pkg.releases());

    assertEquals(Map.of("min", 900, "max", 950), anchor(pkg, "lkp-counter-height").value());
    assertEquals(Map.of("v", 2136L), anchor(pkg, "lkp-wardrobe-rod").value());
    assertFalse(anchor(pkg, "lkp-illuminance-living").degraded());
    assertTrue(anchor(pkg, "lkp-cct-living").degraded());
    // 造价章的金额来自单价库投影（规则 5.15）：城市档命中 breakdown 即取该档，量纲是元每计价单位
    assertEquals(Map.of("min", 60, "max", 68), anchor(pkg, "lkp-price-hydro-labor-sqm").value());
    assertEquals("元/㎡", anchor(pkg, "lkp-price-hydro-labor-sqm").unit());
    assertEquals(1, pkg.gaps().size());
    assertEquals("lkp-tv-distance", pkg.gaps().get(0).lkpId());
    assertEquals("missing_input", pkg.gaps().get(0).reason());
    assertEquals(
        List.of("persona-ergonomics"),
        pkg.personasByDomain().get("ergonomics").stream().map(p -> p.assetId()).toList());
    // 自包含载荷（图 v0.2 §0：成文线不回查任何库）——persona 全文、cr- 判据、禁词随包
    assertEquals("你在为这一家人校核尺寸。", pkg.personasByDomain().get("ergonomics").get(0).identity());
    assertEquals(
        List.of("cr-fabricated-fact", "cr-weak-words"),
        pkg.checksByDomain().get("ergonomics").stream().map(c -> c.assetId()).toList());
    // 判官层判据的投影（规则 4.17）：反例样例与 status 随包下发；三件不齐的样例在投影时丢弃
    CheckAsset judge = pkg.checksByDomain().get("ergonomics").get(0);
    assertEquals("observing", judge.status());
    assertEquals(List.of("你和你太太"), judge.examples().stream().map(CheckExample::bad).toList());
    assertEquals(List.of("依据", "综合考量"), pkg.bannedTermsByDomain().get("ergonomics"));
    assertEquals(List.of(), pkg.withheldAnchors());
    // 标注纪律随真库快照下发（规则 4.10c）：未过门的照标、过门的不要求，判定随包不随时钟
    assertTrue(anchor(pkg, "lkp-cct-living").provenance().annotationRequired());
    assertFalse(anchor(pkg, "lkp-illuminance-living").provenance().annotationRequired());
    assertEquals(
        "GB 50034-2013 表5.2.1", anchor(pkg, "lkp-illuminance-living").provenance().source());
  }

  /**
   * PAID 真库实跑（v2.4 裁决 2026-08-29）：三章 stage-project 产物全量 PAID（规则 9.1）——**求出来的一律下发**，
   * 未过门的语域降为建议口吻并随带标注要求，withheldAnchors 恒空。
   *
   * <p>本用例原名 paidGateWithholdsUnbackedAnchorsFromPublishedReleases，断言的是"未背书的点值/分档值不下发"。
   * 那条纪律整条作废：这次真库实跑里它意味着 budget 的分档值、lighting 的色温点值、ergonomics 的挂杆高度
   * 三条重新回到产物里——正是隐藏档让造价章长期零金额的那一类条目（规范 §14.9）。
   */
  @Test
  void paidDeliversEveryEvaluatedAnchorWithAnnotationDuty() {
    ReportDataPackage pkg =
        reportEvaluationAppService.evaluate(
            List.of("lighting", "ergonomics", "budget"), INPUT, ArtifactEntitlement.PAID);

    assertEquals(ArtifactEntitlement.PAID, pkg.entitlement());
    assertEquals(
        List.of(
            "lkp-budget-confidence-width",
            "lkp-cct-living",
            "lkp-counter-height",
            "lkp-illuminance-living",
            "lkp-passage-main",
            "lkp-price-hydro-labor-sqm",
            "lkp-price-wall-paint",
            "lkp-wardrobe-rod"),
        pkg.anchors().stream().map(ReportAnchor::lkpId).toList());
    assertEquals(List.of(), pkg.withheldAnchors());
    assertEquals(
        AnchorPresentation.THESIS_SUPPORT, anchor(pkg, "lkp-illuminance-living").presentation());
    assertEquals(
        AnchorPresentation.REFERENCE_ONLY, anchor(pkg, "lkp-counter-height").presentation());
    // 三条曾被隐藏的：照常下发，各自带着"这一页得标出来源"的要求
    for (String lkpId :
        List.of("lkp-budget-confidence-width", "lkp-cct-living", "lkp-wardrobe-rod")) {
      assertTrue(anchor(pkg, lkpId).provenance().annotationRequired());
    }
    // 求出了但没依据 → provenance；求不出 → gap-。两条回流信号不混（规则 4.5）
    assertEquals(List.of("lkp-tv-distance"), pkg.gaps().stream().map(g -> g.lkpId()).toList());
  }

  /**
   * 造价章第一次有金额（规则 5.15）：单价库 → 落点投影在真库快照形态上跑通。
   *
   * <p>三件事一起验：①金额进产物（此前造价域只有占比/倍率这类分析值，一个钱数都没有）；②城市档按 匿名画像选档（裁决
   * 2026-08-29：城市档是市场参数不是身份）；③**过期单价照常出金额**，随标注取数 时间与来源（v2.4 推翻原"越界即不出金额"）。描述性属性不投影——material
   * 卡不是数字落点。
   */
  @Test
  void budgetChapterCarriesMoneyFromUnitPriceLibrary() {
    ReportDataPackage pkg =
        reportEvaluationAppService.evaluate(
            List.of("budget"), INPUT, ArtifactEntitlement.PAID, EVALUATED_ON);

    assertEquals(
        List.of("lkp-budget-confidence-width", "lkp-price-hydro-labor-sqm", "lkp-price-wall-paint"),
        pkg.anchors().stream().map(ReportAnchor::lkpId).toList());

    ReportAnchor hydro = anchor(pkg, "lkp-price-hydro-labor-sqm");
    assertEquals(Map.of("min", 60, "max", 68), hydro.value());
    assertEquals("元/㎡", hydro.unit());
    assertEquals("analysis", hydro.numberClass());
    assertEquals(AnchorPresentation.THESIS_SUPPORT, hydro.presentation());
    assertFalse(hydro.provenance().annotationRequired());
    assertEquals(LocalDate.of(2026, 8, 28), hydro.provenance().effectiveFrom());

    ReportAnchor paint = anchor(pkg, "lkp-price-wall-paint");
    assertEquals(Map.of("min", 10, "max", 20), paint.value());
    assertTrue(paint.provenance().annotationRequired());
    assertEquals(LocalDate.of(2026, 8, 1), paint.provenance().effectiveTo());
  }

  /**
   * 跨语言契约形态（contracts rulebook/report_data_package.schema.json）：门禁三字段的**线上字面量**—— 成文线的 pydantic
   * 镜像按同样的字面量解析，改一个字母两侧就对不上，故在此钉死。
   */
  @Test
  void serializesGateFieldsInContractShape() throws Exception {
    ReportDataPackage pkg =
        reportEvaluationAppService.evaluate(
            List.of("ergonomics"), INPUT, ArtifactEntitlement.PAID, EVALUATED_ON);
    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(pkg));

    assertEquals("PAID", json.path("entitlement").asText());
    assertEquals("2026-08-29", json.path("evaluatedOn").asText());
    JsonNode counter = json.path("anchors").path(0);
    assertEquals("lkp-counter-height", counter.path("lkpId").asText());
    assertEquals("REFERENCE_ONLY", counter.path("presentation").asText());
    // 标注纪律的线上字面量（规则 4.10c，v2.4）：成文线按同样的字面量解析，改一个字母两侧就对不上
    JsonNode provenance = counter.path("provenance");
    assertTrue(provenance.path("annotationRequired").asBoolean());
    assertEquals("draft", provenance.path("calibration").asText());
    assertEquals("行业通行", provenance.path("source").asText());
    assertTrue(provenance.path("effectiveTo").isNull());
    // withheldAnchors 恒空（v2.4 取消隐藏档）：字段仍在线上形态里，只是永远没有内容
    assertTrue(json.path("withheldAnchors").isEmpty());
  }

  /** 图 v0.2 §8 首批验证第一件事：同输入重复求值，序列化字节级同输出（规则 8.2 可重放）。 */
  @Test
  void replayProducesByteIdenticalPackage() throws Exception {
    List<String> domains = List.of("lighting", "ergonomics", "budget");
    ReportDataPackage first =
        reportEvaluationAppService.evaluate(domains, INPUT, ArtifactEntitlement.PAID, EVALUATED_ON);
    ReportDataPackage second =
        reportEvaluationAppService.evaluate(domains, INPUT, ArtifactEntitlement.PAID, EVALUATED_ON);

    assertEquals(first, second);
    assertArrayEquals(
        objectMapper.writeValueAsBytes(first), objectMapper.writeValueAsBytes(second));
  }

  /**
   * 规则触发在真库快照形态上跑通（规范 §4.1 三层三触发）：{@code always} 无条件进包、 {@code layout_feature} 按标记命中、{@code answer}
   * 类首版无执行器故不触发。
   *
   * <p>依据（{@code triggeredBy.evidence}）逐字等于画像里那条标记的值——报告里"因为你家厨房是 U 形" 的数据来源（规则 4.3
   * 可追溯性的户型侧对应物）。这是本形态第一次有执行器：此前 {@code layoutFeatures} 契约必填、一处未被消费，四条特征规则从未触发过。
   */
  @Test
  void triggersRulesFromPublishedReleasesByLayoutFeatureAndAlways() {
    ReportDataPackage pkg =
        reportEvaluationAppService.evaluate(
            List.of("lighting", "ergonomics", "budget"),
            INPUT,
            ArtifactEntitlement.PAID,
            EVALUATED_ON);

    assertEquals(
        List.of("rule-practice-ergo-dual-cook-width"),
        pkg.triggeredRulesByDomain().get("ergonomics").stream()
            .map(TriggeredRule::assetId)
            .toList());
    TriggeredRule dualCook = pkg.triggeredRulesByDomain().get("ergonomics").get(0);
    assertEquals("layout_feature", dualCook.triggeredBy().type());
    assertEquals("kitchen_u_shape", dualCook.triggeredBy().feature());
    assertEquals("厨房三面台面围合，中间通道贯通", dualCook.triggeredBy().evidence());
    assertEquals("两人同时下厨时，U型两排间距取上限区间", dualCook.content());

    TriggeredRule hiddenItem = pkg.triggeredRulesByDomain().get("budget").get(0);
    assertEquals("rule-practice-budget-hidden-item-warning", hiddenItem.assetId());
    assertEquals("always", hiddenItem.triggeredBy().type());
    assertNull(hiddenItem.triggeredBy().evidence());

    // 域键恒存在：lighting 快照里一条 rule 都没有，给空列表而不是缺键
    assertEquals(List.of(), pkg.triggeredRulesByDomain().get("lighting"));
  }

  /**
   * 触发条目的**线上字面量**（contracts rulebook/report_data_package.schema.json）：成文线的 pydantic 镜像
   * 按同样的字面量解析，改一个字母两侧就对不上。
   *
   * <p>同时钉住"不下发什么"：{@code trigger}（原始触发条件）与 {@code consumers}（art- 消费方）都不出线—— 契约 {@code
   * additionalProperties:false} 且消费侧 {@code extra="forbid"}，多发一个字段就是整包解析失败。
   */
  @Test
  void serializesTriggeredRulesInContractShape() throws Exception {
    ReportDataPackage pkg =
        reportEvaluationAppService.evaluate(
            List.of("ergonomics"), INPUT, ArtifactEntitlement.PAID, EVALUATED_ON);
    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(pkg));

    JsonNode rule = json.path("triggeredRulesByDomain").path("ergonomics").path(0);
    assertEquals("rule-practice-ergo-dual-cook-width", rule.path("assetId").asText());
    assertEquals("tier-practice", rule.path("layer").asText());
    assertEquals("draft", rule.path("calibration").asText());
    assertEquals("recommended", rule.path("severity").asText());
    assertEquals("layout_feature", rule.path("triggeredBy").path("type").asText());
    assertEquals("kitchen_u_shape", rule.path("triggeredBy").path("feature").asText());
    assertEquals("厨房三面台面围合，中间通道贯通", rule.path("triggeredBy").path("evidence").asText());
    assertTrue(rule.path("trigger").isMissingNode());
    assertTrue(rule.path("consumers").isMissingNode());
  }

  /** 未发布的域拒绝求值（规则 4.12：运行时只读 release，无 release 即无可信内容面）。 */
  @Test
  void refusesDomainWithoutRelease() {
    assertThrows(
        ReleaseNotFoundException.class,
        () ->
            reportEvaluationAppService.evaluate(
                List.of("storage"), INPUT, ArtifactEntitlement.PAID));
  }

  private static ReportAnchor anchor(ReportDataPackage pkg, String lkpId) {
    return pkg.anchors().stream().filter(x -> x.lkpId().equals(lkpId)).findFirst().orElseThrow();
  }
}
