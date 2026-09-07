/**
 * catalog 域的领域模型（资产库，物理落 estate 库的 {@code svc_catalog} schema，《架构对齐》§3.3）。
 *
 * <p>今天只有家具资产尺寸表这一族：一行 = 一个品类的一个档位有多大。消费方是**布局求解** （plan-layout-solve，genpipe-worker）——业主发户型图 →
 * 出三张图 → 确认需求 → 摆家具 → 出效果图， 「摆家具」那一步要问"这个品类的家具多大"，本域是那个能回答的地方。三维渲染**不是**消费方：
 * 渲染拿的是求解产出的家具位置与尺寸，已经定死。
 *
 * <p>品类词与种子行的唯一真源在 ishome-contracts 的 {@code registries/furniture_categories.json} 与 {@code
 * registries/furniture_assets.json}；本包只放库内形态，不复制词表。
 */
package com.ishome.estate.domain.catalog;
