package com.ishome.project.domain.rulebook;

import java.util.List;
import java.util.Map;

/**
 * persona 资产（release 快照内投影，规则 4.13 四件）：随报告数据包整体下发——成文线不回查库（图 v0.2 §0）， prompt
 * 只从本载荷拼装，运行时不读任何人写的文本（规则 4.19；identity 等文本是回路编译产物，非手册原文）。
 *
 * <p>{@code assertionBudget} = 断言预算（判断句谓词 → requires 的 lkp- 列表，规则 5.8）；{@code bannedTerms} = 域内禁词（含
 * domain_extra；跨域公共禁词在 {@link ReleaseSnapshot#bannedTerms()} 物化）。
 */
public record PersonaAsset(
    String assetId,
    String identity,
    List<Object> judgmentSamples,
    List<Object> assertionBudget,
    Map<String, Object> bannedTerms,
    int version) {}
