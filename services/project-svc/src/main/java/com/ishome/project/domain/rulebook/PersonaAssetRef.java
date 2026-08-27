package com.ishome.project.domain.rulebook;

/** persona 资产引用：随报告数据包下发的域语域配置指针（内容随 release 快照，生成侧不回查库）。 */
public record PersonaAssetRef(String assetId, int version) {}
