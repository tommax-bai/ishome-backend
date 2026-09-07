package com.ishome.estate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.f4b6a3.ulid.UlidCreator;
import com.ishome.estate.domain.catalog.FurnitureAsset;
import com.ishome.estate.domain.catalog.SizeTier;
import com.ishome.estate.domain.port.FurnitureAssetRepository;
import com.ishome.estate.testsupport.PostgresIntegrationTestSupport;
import com.ishome.shared.kernel.testsupport.EnabledIfLocalPostgres;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 家具资产尺寸表 PG 实跑（Flyway 迁移 + MyBatis 只读实现，独立 schema svc_catalog_it）：本地 PG
 * （localhost:15432）可达才执行，不可达跳过。
 *
 * <p>本测试验的是**表与只读入口**：闭集拦不拦得住、取行键唯一不唯一、来路栏原样存不存得住、 求解按品类/按品类+档取不取得到。种子从契约仓灌进来那一半由 {@code
 * scripts/catalog/test_import_furniture_assets.py} 黑盒跑真脚本验（灌得进、幂等、契约改了能重灌、 表外品类拒灌）——灌库是那条路径上的事，在这里
 * mock 一份种子等于验了个假的。
 *
 * <p>夹具行用 JdbcTemplate 直接写：本服务今天对这张表**只读**，没有写入口可借；这几条验的本来 也是库的约束，绕开应用层写才验得到它。
 */
@SpringBootTest
@EnabledIfLocalPostgres
@Import(PostgresIntegrationTestSupport.CleanMigrateConfig.class)
class FurnitureAssetPersistenceIntegrationTest {

  private static final String CATALOG = PostgresIntegrationTestSupport.CATALOG_SCHEMA;

  /** 来路三种记法各取一条真样本（契约仓 furniture_assets.md 逐字），验的就是它们原样存得住。 */
  private static final String PROVENANCE_CITED =
      "甲 furnish_mock.py:53-54 依据注释：「双人床。1.8×2.0m 是国标双人床最常见的档位；" + "0.45m 是床垫+床架的常见高度。」";

  private static final String PROVENANCE_NO_SOURCE =
      "甲 furnish_mock.py:56——无定源（常量无 docstring）；乙 design-package-full.json:1142 同值";

  private static final String PROVENANCE_FIXTURE = "乙 design-package-full.json:1153——拟真填充、无真跑来路";

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    PostgresIntegrationTestSupport.register(registry);
  }

  @Autowired FurnitureAssetRepository furnitureAssetRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  /** 表建得起来：迁移在 svc_catalog_it 里建出两张表，且 catalog 的表不落进 svc_estate（schema 独立可拆走）。 */
  @Test
  void migrationCreatesCatalogTablesInItsOwnSchema() {
    List<String> tables =
        jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = ?"
                + " ORDER BY table_name",
            String.class,
            CATALOG);
    assertEquals(List.of("furniture_assets", "furniture_categories"), tables);

    Integer strays =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = ?"
                + " AND table_name LIKE 'furniture%'",
            Integer.class, PostgresIntegrationTestSupport.SCHEMA);
    assertEquals(0, strays);
  }

  /** 按品类取候选（求解的主查询）：三档全取到，按宽度升序——候选顺序必须确定。 */
  @Test
  void listByCategoryReturnsEveryTierOrderedBySize() {
    givenCategory("bed");
    givenAsset("asset-bed-large", "bed", "large", 1.8, 2.0, 0.45, PROVENANCE_CITED);
    givenAsset("asset-bed-small", "bed", "small", 1.2, 2.0, 0.45, PROVENANCE_FIXTURE);
    givenAsset("asset-bed-standard", "bed", "standard", 1.5, 2.0, 0.45, PROVENANCE_FIXTURE);

    List<FurnitureAsset> candidates = furnitureAssetRepository.listByCategory("bed");

    assertEquals(
        List.of("asset-bed-small", "asset-bed-standard", "asset-bed-large"),
        candidates.stream().map(FurnitureAsset::assetId).toList());
    assertEquals(SizeTier.SMALL, candidates.get(0).sizeTier());
    assertEquals(1.2, candidates.get(0).widthM());
    assertEquals(2.0, candidates.get(0).depthM());
    assertEquals(0.45, candidates.get(0).heightM());
    // 今天没有一行对应真货：sku_ref 全空，size_source 全是常规档位
    assertTrue(candidates.stream().allMatch(asset -> asset.skuRef() == null));
    assertTrue(candidates.stream().allMatch(asset -> "regular-tier".equals(asset.sizeSource())));
  }

  /** 按品类 + 档取那一行；取不到即空——降级（退到 standard / 进 unplaced）由求解侧决定，不在这一层猜。 */
  @Test
  void findByCategoryAndSizeTierReturnsSingleRowOrEmpty() {
    givenCategory("wardrobe");
    givenAsset(
        "asset-wardrobe-standard", "wardrobe", "standard", 1.6, 0.6, 2.2, PROVENANCE_FIXTURE);

    Optional<FurnitureAsset> standard =
        furnitureAssetRepository.findByCategoryAndSizeTier("wardrobe", SizeTier.STANDARD);
    assertTrue(standard.isPresent());
    assertEquals(1.6, standard.get().widthM());

    // 该品类有 standard 但没有 large：这一层如实答"没有"
    assertTrue(
        furnitureAssetRepository.findByCategoryAndSizeTier("wardrobe", SizeTier.LARGE).isEmpty());
    // 该品类一行都没有
    assertTrue(furnitureAssetRepository.listByCategory("hammock").isEmpty());
  }

  /** 闭集外的品类写不进去：约束落在 svc_catalog 内部的外键上，品类表里没有的词进不了资产表。 */
  @Test
  void categoryOutsideClosedSetIsRejected() {
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            givenAsset(
                "asset-hammock-standard",
                "hammock",
                "standard",
                2.0,
                0.8,
                0.5,
                PROVENANCE_FIXTURE));
  }

  /** (category, size_tier) 是取行键：同品类同档的第二行当场被拒——求解取行只能取到一行。 */
  @Test
  void duplicateCategoryAndSizeTierIsRejected() {
    givenCategory("sofa");
    givenAsset("asset-sofa-standard", "sofa", "standard", 2.2, 0.9, 0.8, PROVENANCE_CITED);

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            givenAsset(
                "asset-sofa-standard-again",
                "sofa",
                "standard",
                2.4,
                0.9,
                0.8,
                PROVENANCE_FIXTURE));
  }

  /** 没定源不因为入库而消失：「无定源」那句话逐字存住、原样读回；来路栏空着一律拒收。 */
  @Test
  void provenanceWithoutDefiniteSourceIsStoredVerbatim() {
    givenCategory("nightstand");
    givenAsset(
        "asset-nightstand-standard",
        "nightstand",
        "standard",
        0.45,
        0.4,
        0.55,
        PROVENANCE_NO_SOURCE);

    FurnitureAsset reloaded =
        furnitureAssetRepository
            .findByCategoryAndSizeTier("nightstand", SizeTier.STANDARD)
            .orElseThrow();
    assertEquals(PROVENANCE_NO_SOURCE, reloaded.provenance());
    // size_source 仍是常规档位那一档：来路分三种、size_source 全同，两栏不互相代替
    assertEquals("regular-tier", reloaded.sizeSource());

    givenCategory("bookshelf");
    assertThrows(
        DataIntegrityViolationException.class,
        () -> givenAsset("asset-bookshelf-standard", "bookshelf", "standard", 0.8, 0.3, 2.0, "  "));
  }

  /** 命名禁纯序号：asset-001 这类过得了字符集、过不了语义命名那道列约束。 */
  @Test
  void pureOrdinalAssetIdIsRejected() {
    givenCategory("desk");
    assertThrows(
        DataIntegrityViolationException.class,
        () -> givenAsset("asset-001", "desk", "standard", 1.2, 0.6, 0.75, PROVENANCE_NO_SOURCE));
  }

  /** 档位闭集：三档之外写不进去。 */
  @Test
  void sizeTierOutsideClosedSetIsRejected() {
    givenCategory("toilet");
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            givenAsset("asset-toilet-huge", "toilet", "huge", 0.4, 0.7, 0.75, PROVENANCE_FIXTURE));
  }

  private void givenCategory(String category) {
    jdbcTemplate.update(
        "INSERT INTO "
            + CATALOG
            + ".furniture_categories (id, category, semantics)"
            + " VALUES (?, ?, ?) ON CONFLICT (category) DO NOTHING",
        UlidCreator.getUlid().toString(),
        category,
        "集成测试夹具：" + category);
  }

  private void givenAsset(
      String assetId,
      String category,
      String sizeTier,
      double widthM,
      double depthM,
      double heightM,
      String provenance) {
    jdbcTemplate.update(
        "INSERT INTO "
            + CATALOG
            + ".furniture_assets (id, asset_id, category, size_tier,"
            + " width_m, depth_m, height_m, sku_ref, size_source, provenance)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, NULL, 'regular-tier', ?)",
        UlidCreator.getUlid().toString(),
        assetId,
        category,
        sizeTier,
        widthM,
        depthM,
        heightM,
        provenance);
  }
}
