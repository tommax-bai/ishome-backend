package com.ishome.project.domain.rulebook;

import java.util.Map;

/**
 * 求值输入 = slots 派生的匿名结构（图 v0.2 §0：生成侧不知用户是谁——本记录**禁止**携带任何用户/项目标识）。
 *
 * <p>身高族供人体工学公式（规范 §5.2）；{@code layoutFeatures} 为 estate 标注户型特征（规则 6.3
 * 触发字段全集：kitchen_shape/entrance_shape/sunken_bathroom/…），键值均为字符串，来自标注不来自解析 （首实装避开 floorplan-parse，图
 * v0.2 §8）。缺失字段用 null——求值产出 gap- 记录，不阻塞。
 *
 * <p>{@code cityTier} 供造价章按档选单价（规则 5.15）。裁决 2026-08-29：**城市档是市场参数不是身份**——
 * 一个城市几十万住户不具标识性，缺它则造价章要么失真（全国粗档当本地价）要么哑火。取值是**单价资产 {@code props.breakdown} 的档名词面**（如"一线"），不另立一套
 * tier 枚举：档名由数据自带，另立即造出 同概念两套名（规则 1.8 第四条），还要多一张只会漂移的映射表。缺席 → 取全国粗档区间。
 */
public record EvaluationInput(
    Integer chiefHeightMm,
    Integer tallestHeightMm,
    Integer eyeHeightMm,
    Integer tvScreenHeightMm,
    Map<String, String> layoutFeatures,
    String cityTier) {}
