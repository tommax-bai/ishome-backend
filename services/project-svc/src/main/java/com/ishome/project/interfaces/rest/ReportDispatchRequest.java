package com.ishome.project.interfaces.rest;

import com.ishome.project.application.ReportDispatchCommand;
import com.ishome.project.domain.rulebook.ArtifactEntitlement;
import com.ishome.project.domain.rulebook.EvaluationInput;
import java.util.List;
import java.util.Map;

/**
 * 报告派发请求体：一次求值 + 一次派发要的全部调用方知识。
 *
 * <p>匿名纪律（图 v0.2 §0 生成侧不知用户是谁）在**入参形态上**执行——本请求体里没有 projectId/userId 的位置， 画像字段就是求值输入本身。调用方（里程碑引擎 /
 * bff）负责把 slots 派生成这份匿名结构。
 */
public record ReportDispatchRequest(
    List<String> domains,
    EvaluationInput anonymousProfile,
    ArtifactEntitlement entitlement,
    Map<String, List<String>> lockedTextsByArtifact) {

  ReportDispatchCommand toCommand() {
    return new ReportDispatchCommand(
        domains,
        anonymousProfile,
        entitlement,
        lockedTextsByArtifact == null ? Map.of() : lockedTextsByArtifact);
  }
}
