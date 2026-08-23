/**
 * application 层——用例编排：事务边界唯一落点（@Transactional 只许出现在这里，ArchUnit 强制）、 跨模块协调、经 outbox 发领域事件。
 *
 * <p>用例服务命名 XxxAppService；入参 XxxCommand（写）/ XxxQuery（读），出参 XxxResult（规范 §2.2）。
 */
package com.ishome.channel.application;
