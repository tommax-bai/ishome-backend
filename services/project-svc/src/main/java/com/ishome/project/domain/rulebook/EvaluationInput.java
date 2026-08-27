package com.ishome.project.domain.rulebook;

import java.util.Map;

/**
 * 求值输入 = slots 派生的匿名结构（图 v0.2 §0：生成侧不知用户是谁——本记录**禁止**携带任何用户/项目标识）。
 *
 * <p>身高族供人体工学公式（规范 §5.2）；{@code layoutFeatures} 为 estate 标注户型特征（规则 6.3
 * 触发字段全集：kitchen_shape/entrance_shape/sunken_bathroom/…），键值均为字符串，来自标注不来自解析 （首实装避开 floorplan-parse，图
 * v0.2 §8）。缺失字段用 null——求值产出 gap- 记录，不阻塞。
 */
public record EvaluationInput(
    Integer chiefHeightMm,
    Integer tallestHeightMm,
    Integer eyeHeightMm,
    Integer tvScreenHeightMm,
    Map<String, String> layoutFeatures) {}
