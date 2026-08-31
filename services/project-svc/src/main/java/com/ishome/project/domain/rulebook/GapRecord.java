package com.ishome.project.domain.rulebook;

/**
 * 求值缺口记录（gap-，图 v0.2 §2：查不到 → 记录随产物回流，不阻塞）。{@code reason} 枚举三值： {@code missing_input}（匿名输入缺字段）/
 * {@code formula_not_implemented}（公式无可执行形态）/ {@code empty_definition}（参数无值无公式）。
 */
public record GapRecord(String lkpId, String basisTag, String reason, String detail) {

  /**
   * {@code basisTag}（{@code {domain}@v{n}}）与 {@link ReportAnchor} 同一口径：**缺口也要能切回它自己那个域**。
   *
   * <p>2026-08-31 第一次六章整册真跑立案：缺口原先没有域，成文线 {@code gaps=package.gaps} 把整册缺口原样发给
   * 每一章（落点是按域切的，缺口漏了），于是各章为**别的章的缺口**写坦白卡——同一条缺口在四章各说一遍， 且 storage 的「总收纳延米数」把该域禁词「延米」带进了 softdeco
   * 的正文。消费侧按本字段切本域缺口。
   */
  public GapRecord {}
}
