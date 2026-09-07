package com.ishome.estate.testsupport;

import com.ishome.shared.kernel.testsupport.LocalPostgres;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;

/** 持久化集成测试装配：独立 schema（与开发数据互不污染）+ 每次测试上下文启动时 clean → migrate（测试间互不污染）。 */
public final class PostgresIntegrationTestSupport {

  /** schema-per-service 的测试位：正式 schema svc_estate 不被集成测试触碰。 */
  public static final String SCHEMA = "svc_estate_it";

  /** V1 的 catalog 测试位：正式 schema svc_catalog 同样不被触碰（placeholder 注入）。 */
  public static final String CATALOG_SCHEMA = "svc_catalog_it";

  private PostgresIntegrationTestSupport() {}

  /** 注册数据源与 Flyway 属性（坐标经 ISHOME_DB_* 环境变量覆盖，默认本地开发 PG）。 */
  public static void register(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> LocalPostgres.jdbcUrl(SCHEMA));
    registry.add("spring.datasource.username", LocalPostgres::username);
    registry.add("spring.datasource.password", LocalPostgres::password);
    registry.add("spring.flyway.schemas", () -> SCHEMA + "," + CATALOG_SCHEMA);
    registry.add("spring.flyway.default-schema", () -> SCHEMA);
    registry.add("spring.flyway.clean-disabled", () -> "false");
    registry.add("spring.flyway.placeholders.catalog_schema", () -> CATALOG_SCHEMA);
    registry.add("mybatis-plus.configuration-properties.catalogSchema", () -> CATALOG_SCHEMA);
  }

  /** clean → migrate：集成测试每个 Spring 上下文从空表起步。 */
  @TestConfiguration
  public static class CleanMigrateConfig {

    @Bean
    public FlywayMigrationStrategy cleanThenMigrate() {
      return flyway -> {
        flyway.clean();
        flyway.migrate();
      };
    }
  }
}
