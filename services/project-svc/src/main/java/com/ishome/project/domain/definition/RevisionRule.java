package com.ishome.project.domain.definition;

import java.util.List;

/**
 * 修订循环规则：dimensions = 修订维度词表（chat 受限映射的枚举约束）， budgetRounds = 每里程碑修订轮数软预算（预算判定在 project-svc，接近时 chat
 * 话术收束）。
 */
public record RevisionRule(List<String> dimensions, int budgetRounds) {}
