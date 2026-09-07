package com.ishome.estate.domain.catalog;

/**
 * 家具资产一行：一个品类的一个档位有多大（{@code svc_catalog.furniture_assets}）。
 *
 * <p>唯一真源 ishome-contracts {@code registries/furniture_assets.json}（24 行常规档位种子）， 字段形状逐字对上 {@code
 * LayoutSolveRequest.candidates[]}——求解按 {@code category + size_pref} 取行，
 * 把长宽高逐字装进候选，**求解器不改尺寸**（尺寸来自候选、位置由求解算出）。
 *
 * <p>量纲入名，**整数毫米**，与《开发规范》§4.1「全链路毫米」一致、本表无例外（用户裁决 2026-09-07
 * "量纲统一毫米，家具资产表不例外"）。尺寸是**点值不是区间**（同日裁决"档位给点值"）；用 {@code int} 而非浮点， 半毫米那种没有来源的精度在类型上就写不出来。
 *
 * @param assetId 资产行标识，语义命名、禁纯序号（形如 {@code asset-bed-large}，前缀 {@code asset-} 即命名空间）。 已发布的 id
 *     不改名不复用，哪怕那一行被撤下
 * @param category 品类，取值在《家具品类词表》闭集内**且未退役**（DB 侧由指向在役品类的复合外键约束）
 * @param skuRef 型号引用，**可空**；今天 24 行全空——没有一行对应真货
 * @param sizeSource 尺寸来源标（闭集，今天只有 {@code regular-tier}）：这批数是哪一类来源。 照 render3d {@code
 *     heights_source} 的先例——一版布局按真实家具算的还是按常规档位算的，看图的人有权知道。 不做成 Java 枚举：加档是数据侧的事（接真实家具数据那天登记），闭集由 DB
 *     CHECK 守
 * @param provenance 这一行的数出自哪儿（逐行出处）。与 {@code sizeSource} **是两栏、不能互相代替**： 24 行的 sizeSource
 *     全同、来路却分三种（抄依据注释 5 / 无定源 7 / 拟真填充 12）， 折进 sizeSource 就会把 19 行没有定源这件事盖掉。**没定源不因为入库而消失**
 */
public record FurnitureAsset(
    String assetId,
    String category,
    SizeTier sizeTier,
    int widthMm,
    int depthMm,
    int heightMm,
    String skuRef,
    String sizeSource,
    String provenance) {}
