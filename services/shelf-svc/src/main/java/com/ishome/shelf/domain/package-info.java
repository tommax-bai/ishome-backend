/**
 * domain 层——实体、值对象、领域规则（XxxPolicy / XxxCalculator / XxxValidator）、repository 接口（port）。
 *
 * <p>禁止 import spring-web / mybatis / servlet 任何类（ArchUnit 强制）——领域规则不感知技术细节， 换存储/换框架不动
 * domain。量纲入名：wallLengthMm / usableAreaSqm / priceCents（规范 §4.1）。
 */
package com.ishome.shelf.domain;
