/**
 * 仓储 PG 实现（MyBatis-Plus + Flyway）——真相在表。
 *
 * <p>本服务持两个 schema：{@code svc_estate}（小区/户型/交付日历，表未起）与 {@code svc_catalog} （资产库，逻辑独立域、物理落
 * estate，《架构对齐》§3.3）。catalog 侧的 schema 名经 MyBatis 配置变量 {@code ${catalogSchema}}
 * 注入（正式=svc_catalog，集成测试=svc_catalog_it），与 Flyway placeholder {@code catalog_schema} 指同一事实、两处注入。
 *
 * <p>命名随规范 §2.1/§2.2：XxxRepositoryImpl 内部调 XxxMapper，持久化对象 XxxPO。
 */
package com.ishome.estate.infrastructure.persistence;
