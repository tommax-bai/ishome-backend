package com.ishome.project.domain.rulebook;

/**
 * 产物权益档（规范 §3.1 产物登记表的"权益"列）：求值线的**入参**，不是规则引擎的知识。
 *
 * <p>纪律出处 "contracts 只进契约不进内容"（规则 4.12）——{@code art-} 产物清单连同它的权益列住在 contracts {@code
 * registries/artifacts.md}，本模块**禁止**复制该表。谁调用谁知道自己在生成哪个产物， 由调用方把该产物的权益列作为入参传进来；规则引擎只按档位执行纪律，不认识任何
 * {@code art-}。
 *
 * <p>一次求值只服务一个权益档：混档产物（如 art-quotation-checklist 的 "FREE 3 条 / PAID 全表"）
 * 由调用方分两次求值，不在同一个报告数据包内混档——包内混档会让降档判定失去唯一口径。
 */
public enum ArtifactEntitlement {
  /** 免费产物（stage-catalog 获客层为主）：不受规则 4.10 的 PAID 禁令，未背书条目降档呈现即可。 */
  FREE,

  /** 付费产物（stage-project 深化层，规则 9.1 全量 PAID）：受规则 4.10 禁令，未背书条目降档或隐藏。 */
  PAID
}
