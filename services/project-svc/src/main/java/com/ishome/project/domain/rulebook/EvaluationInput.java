package com.ishome.project.domain.rulebook;

import java.util.Map;

/**
 * 求值输入 = slots 派生的匿名结构（图 v0.2 §0：生成侧不知用户是谁——本记录**禁止**携带任何用户/项目标识）。
 *
 * <p>身高族供人体工学公式（规范 §5.2）。缺失字段用 null——求值产出 gap- 记录，不阻塞。
 *
 * <p>{@code layoutFeatures} = **户型特征标记集**（规则 6.3 触发字段）：**键＝标记名**（取值闭集见 contracts {@code
 * rulebook/layout_features.json}），**值＝这条标记成立的依据**（人话，如"阳台内有洗衣机设备位"）。
 * 匹配语义是**键存在即触发、值不参与匹配**，值的用途是依据留痕——见 {@link RuleTriggerPolicy} 与 {@link
 * TriggerEvidence}。闭集校验在两侧做：生产方（解析产出）校验键 ⊆ 闭集，核验侧校验规则引用的标记名 ∈ 闭集 （{@code
 * scripts/rulebook/verify_seeds.py}）。本记录**不持有闭集**——持有即多一张会与数据漂移的表 （同城市档裁决 2026-08-29）。
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
    String cityTier,
    Double buildingAreaSqm,
    Integer floorAreaRatioPercent) {

  /**
   * 套内面积（㎡）= 建筑面积 × 得房率。缺任一项返回 {@code null}——**不猜**，由调用方记 gap-。
   *
   * <p>这两个字段 2026-08-31 加入，理由与 {@code cityTier} 同族：**它们是房屋属性不是身份**，
   * 且都是业主自己就知道的事实（购房合同上就有），会话侧本来就在收（{@code floorplan/building_area_sqm}、
   * {@code floorplan/floor_area_ratio}，得房率按百分数的数字收，如 81）。
   *
   * <p>为什么值得单独加这两个字段：**报告里一切"量"的地基**。真跑实测（2026-08-31）造价章有五条
   * calibrated 单价却算不出任何总价，收纳章说不出全屋要多少米收纳——缺的从来不是单价，是量。
   * 而量的最短来源就是这两个数：套内面积一有，`收纳总长 = 套内面积 × 收纳密度基准` 当场就算得出来，
   * 不必等定稿平面。
   *
   * <p>问业主要这两个数**不违反"不许把设计判断推给业主"**（用户裁决 2026-08-31：「客户怎么知道地毯
   * 应该用多长的呢？这应该是我们告诉他的」）——分界是：**他知道的事实该问**（面积、几口人），
   * **他不知道的设计判断不许问**（地毯多大、要多少延米收纳）。
   */
  public Double netAreaSqm() {
    if (buildingAreaSqm == null || floorAreaRatioPercent == null) {
      return null;
    }
    return buildingAreaSqm * floorAreaRatioPercent / 100.0;
  }
}
