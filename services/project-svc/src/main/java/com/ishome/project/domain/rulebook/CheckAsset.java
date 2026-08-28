package com.ishome.project.domain.rulebook;

import java.util.List;

/**
 * check 形态资产（纪律，规则 4.10b）：确定性拦截/降档规则，无 calibration、锚 {@code decidedBy} 裁决记录， 数值阈值只经 {@code
 * thresholdRefs} 引用 lkp- 参数。随报告数据包下发，成文线出口过检·规则层按 checkType 物化执行 （图 v0.2 §3——cr- 判据是 release
 * 数据，不是代码里的硬编码清单）。
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
    int version) {}
