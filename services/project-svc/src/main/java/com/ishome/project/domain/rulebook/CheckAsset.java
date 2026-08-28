package com.ishome.project.domain.rulebook;

import java.util.List;

/**
 * check 形态资产（纪律，规则 4.10b）：确定性拦截/降档规则，无 calibration、锚 {@code decidedBy} 裁决记录， 数值阈值只经 {@code
 * thresholdRefs} 引用 lkp- 参数。随报告数据包下发，成文线出口过检·规则层按 checkType 物化执行 （图 v0.2 §3——cr- 判据是 release
 * 数据，不是代码里的硬编码清单）。
 *
 * <p>{@code examples} 非空即**判官层**判据（{@code checkType=semantic_judge}）：规则层判不出的语义违规按反例样例交判官读 （规则 4.10c
 * 已写明"这句算不算判断句"没有确定性判据、机检不假实现）。{@code status} = 规则 4.17 入册门禁第二道的数据侧开关： {@code observing} 只记录不拦截 /
 * {@code active} 命中即违规 / {@code retired} 停用留档。 **拦截与否由数据决定不由代码分支决定** ——转正走发版，回滚=切回旧 release_tag。
 */
public record CheckAsset(
    String assetId,
    String checkType,
    List<String> scope,
    String pattern,
    String requirement,
    String message,
    String decidedBy,
    List<String> thresholdRefs,
    List<CheckExample> examples,
    String status,
    int version) {}
