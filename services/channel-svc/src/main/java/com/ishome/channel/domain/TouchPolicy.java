package com.ishome.channel.domain;

/**
 * 触达策略引擎落点（术语表：触达 = touch，规范 §4.2）。
 *
 * <p>各平台的发送窗口、频控、配额规则做成策略数据，不写死代码（对齐文档 §6.3）—— 平台规则会变，规则变更只改配置。能力缺失时的降级决策集中在这里，不散落在各调用点（R5）。
 *
 * <p>TODO: 随首个真实触达用例实装（策略表结构 + 判定方法），当前为落点占位。
 */
public final class TouchPolicy {

  private TouchPolicy() {}
}
