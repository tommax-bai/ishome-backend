package com.ishome.project.domain;

import com.ishome.project.domain.definition.OnEnterAction;
import java.util.List;

/** checkCompletion 的迁移判定结果：从哪迁到哪 + 目标里程碑的 on_enter 动作声明（数据）。 */
public record MilestoneTransition(
    String fromMilestoneId, String toMilestoneId, List<OnEnterAction> onEnterActions) {}
