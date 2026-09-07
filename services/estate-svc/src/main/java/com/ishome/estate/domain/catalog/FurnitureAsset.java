package com.ishome.estate.domain.catalog;

/**
 * 家具资产一行：一个品类的一个档位有多大（{@code svc_catalog.furniture_assets}）。
 *
 * <p>唯一真源 ishome-contracts {@code registries/furniture_assets.json}（25 行常规档位种子）， 字段形状逐字对上 {@code
 * LayoutSolveRequest.candidates[]}——求解按 {@code category + size_pref} 取行，
 * 把长宽高逐字装进候选，**求解器不改尺寸**（尺寸来自候选、位置由求解算出）。
 *
 * <p>量纲入名，单位**米**：这一处与《开发规范》§4.1 的"长度一律 _mm"冲突，取米——字段名由跨仓契约 定死，求解侧逐字读 {@code
 * width_m/depth_m/height_m}，改毫米等于改协议。尺寸是**点值不是区间** （用户裁决 2026-09-07"档位给点值"）。
 *
 * @param assetId 资产行标识，语义命名、禁纯序号（形如 {@code asset-bed-large}，前缀 {@code asset-} 即命名空间）
 * @param category 品类，取值在《家具品类词表》闭集内（DB 侧由 {@code furniture_categories} 外键约束）
 * @param skuRef 型号引用，**可空**；今天 25 行全空——没有一行对应真货
 * @param sizeSource 尺寸来源标（闭集，今天只有 {@code regular-tier}）：这批数是哪一类来源。 照 render3d {@code
 *     heights_source} 的先例——一版布局按真实家具算的还是按常规档位算的，看图的人有权知道。 不做成 Java 枚举：加档是数据侧的事（接真实家具数据那天登记），闭集由 DB
 *     CHECK 守
 * @param provenance 这一行的数出自哪儿（逐行出处）。与 {@code sizeSource} **是两栏、不能互相代替**： 25 行的 sizeSource
 *     全同、来路却分三种（抄依据注释 6 / 无定源 7 / 拟真填充 12）， 折进 sizeSource 就会把 19 行没有定源这件事盖掉。**没定源不因为入库而消失**
 */
public record FurnitureAsset(
    String assetId,
    String category,
    SizeTier sizeTier,
    double widthM,
    double depthM,
    double heightM,
    String skuRef,
    String sizeSource,
    String provenance) {}
