/**
 * 仓储 PG 实现（MyBatis-Plus + Flyway，svc_project schema，对齐文档 §5.1）——真相在表。
 *
 * <p>命名随规范 §2.1/§2.2：XxxRepositoryImpl 内部调 XxxMapper，持久化对象 XxxPO；枚举与 DB 存储字符串逐字一致。 单测用的内存假实现在 test
 * 源集 testsupport 包，不在此处。
 */
package com.ishome.project.infrastructure.persistence;
