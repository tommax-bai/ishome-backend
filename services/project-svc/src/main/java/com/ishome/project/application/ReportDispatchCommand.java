package com.ishome.project.application;

import com.ishome.project.domain.rulebook.ArtifactEntitlement;
import com.ishome.project.domain.rulebook.EvaluationInput;
import java.util.List;
import java.util.Map;

/**
 * 一次报告派发的入参：求值范围 + 匿名输入 + 产物档位 + 该产物必挂的锁定文案。
 *
 * <p>四件全是**调用方知识**：哪些域要成文、这份报告服务哪个 art-（决定 FREE/PAID 与必挂集）、业主的匿名画像。 规则引擎不持有 art- 清单（规则
 * 4.12），所以档位与必挂集只能传进来，不能在这一侧查表。
 *
 * <p>{@code reportId} **不在这里**：它由 project-svc 在派发时铸造（裁决③ 幂等键在重试的那一侧），调用方给不了
 * 也不该给——给了就等于把幂等键的所有权挪到了不负责重试的一边。
 */
public record ReportDispatchCommand(
    List<String> domains,
    EvaluationInput anonymousProfile,
    ArtifactEntitlement entitlement,
    Map<String, List<String>> lockedTextsByArtifact) {}
