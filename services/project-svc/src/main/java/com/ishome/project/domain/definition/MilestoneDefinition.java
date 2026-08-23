package com.ishome.project.domain.definition;

import java.util.List;

/**
 * 单个里程碑的定义（纯数据）。
 *
 * @param id 里程碑标识（如 "M0"、"M0.5"），DB current_milestone 存同一字符串
 * @param name 展示名（如 "建档"）
 * @param producedArtifactType 本里程碑产出物类型；无生成产物（判据纯槽位）时为 null
 * @param requiredSlots 槽位 schema——chat 消费切片（抽取提示/选项），project 不使用其语义
 * @param allowedActions 动作白名单——chat 消费切片（封闭动作集按当前里程碑裁剪）
 * @param completionCriteria 完成判据（仅简单谓词，布尔求值在 MilestoneCompletionPolicy）
 * @param onEnterActions 进入本里程碑时的动作声明（数据；执行编排在 application 层）
 * @param revisionRule 修订循环规则（维度词表 + 轮数软预算）；本里程碑无修订循环时为 null
 */
public record MilestoneDefinition(
    String id,
    String name,
    String producedArtifactType,
    List<SlotDefinition> requiredSlots,
    List<String> allowedActions,
    CompletionCriteria completionCriteria,
    List<OnEnterAction> onEnterActions,
    RevisionRule revisionRule) {}
