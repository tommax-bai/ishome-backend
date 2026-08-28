package com.ishome.project;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.ulid.UlidCreator;
import com.ishome.project.application.ReportEvaluationAppService;
import com.ishome.project.domain.rulebook.EvaluationInput;
import com.ishome.project.domain.rulebook.ReleaseNotFoundException;
import com.ishome.project.domain.rulebook.ReleaseRef;
import com.ishome.project.domain.rulebook.ReportAnchor;
import com.ishome.project.domain.rulebook.ReportDataPackage;
import com.ishome.project.testsupport.PostgresIntegrationTestSupport;
import com.ishome.shared.kernel.testsupport.EnabledIfLocalPostgres;
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

  /** estate 标注户型夹具（规则 6.3 触发字段）+ 匿名身高族；tvScreenHeightMm 缺失 → gap 不阻塞。 */
  private static final EvaluationInput INPUT =
      new EvaluationInput(
          1700,
          1780,
          1600,
          null,
          Map.of("kitchen_shape", "U", "entrance_shape", "side", "sunken_bathroom", "true"));

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
        {"release_tag":"ergonomics@v1","domain":"ergonomics","assets":{"parameters":[
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
           "decided_by":"规范规则 5.9","threshold_refs":[],"version":1}],
          "vocabularies":[{"asset_id":"vocab-banned-methodology","kind":"banned_term",
           "terms":{"methodology":["依据","综合考量"]},"version":1}]}}
        """);
    insertRelease(
        "budget",
        "budget@v1",
        """
        {"release_tag":"budget@v1","domain":"budget","assets":{"parameters":[
          {"asset_id":"lkp-budget-confidence-width","name":"置信到区间宽度的映射","number_class":"analysis",
           "value":{"high":"±10%","medium":"±20%","low":"±35%"},"formula":null,"unit":null,
           "calibration":"draft","source":"内部规范 §5.9","version":1}],
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

  /** 三域求值：公式代入、直取、降档标记、gap 记录、release 引用集齐全。 */
  @Test
  void evaluatesThreeDomainsAgainstPublishedReleases() {
    ReportDataPackage pkg =
        reportEvaluationAppService.evaluate(List.of("lighting", "ergonomics", "budget"), INPUT);

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
    assertEquals(1, pkg.gaps().size());
    assertEquals("lkp-tv-distance", pkg.gaps().get(0).lkpId());
    assertEquals("missing_input", pkg.gaps().get(0).reason());
    assertEquals(
        List.of("persona-ergonomics"),
        pkg.personasByDomain().get("ergonomics").stream().map(p -> p.assetId()).toList());
    // 自包含载荷（图 v0.2 §0：成文线不回查任何库）——persona 全文、cr- 判据、禁词随包
    assertEquals("你在为这一家人校核尺寸。", pkg.personasByDomain().get("ergonomics").get(0).identity());
    assertEquals(
        List.of("cr-weak-words"),
        pkg.checksByDomain().get("ergonomics").stream().map(c -> c.assetId()).toList());
    assertEquals(List.of("依据", "综合考量"), pkg.bannedTermsByDomain().get("ergonomics"));
  }

  /** 图 v0.2 §8 首批验证第一件事：同输入重复求值，序列化字节级同输出（规则 8.2 可重放）。 */
  @Test
  void replayProducesByteIdenticalPackage() throws Exception {
    List<String> domains = List.of("lighting", "ergonomics", "budget");
    ReportDataPackage first = reportEvaluationAppService.evaluate(domains, INPUT);
    ReportDataPackage second = reportEvaluationAppService.evaluate(domains, INPUT);

    assertEquals(first, second);
    assertArrayEquals(
        objectMapper.writeValueAsBytes(first), objectMapper.writeValueAsBytes(second));
  }

  /** 未发布的域拒绝求值（规则 4.12：运行时只读 release，无 release 即无可信内容面）。 */
  @Test
  void refusesDomainWithoutRelease() {
    assertThrows(
        ReleaseNotFoundException.class,
        () -> reportEvaluationAppService.evaluate(List.of("storage"), INPUT));
  }

  private static ReportAnchor anchor(ReportDataPackage pkg, String lkpId) {
    return pkg.anchors().stream().filter(x -> x.lkpId().equals(lkpId)).findFirst().orElseThrow();
  }
}
