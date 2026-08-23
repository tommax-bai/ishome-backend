package com.ishome.project.application;

/**
 * 修订预算判定结果：有余额时建 revision task（taskId 非空，roundNo = 本轮轮次）； 预算耗尽时 budgetExhausted = true 且不建任务（chat
 * 依此选"引导收束"话术）。
 */
public record RevisionResult(
    boolean budgetExhausted, int roundNo, int budgetRounds, String taskId) {}
