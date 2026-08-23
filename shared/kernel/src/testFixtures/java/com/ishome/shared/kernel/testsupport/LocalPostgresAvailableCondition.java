package com.ishome.shared.kernel.testsupport;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/** {@link EnabledIfLocalPostgres} 的判定实现：TCP 探测本地 PG，可达启用、不可达跳过。 */
public final class LocalPostgresAvailableCondition implements ExecutionCondition {

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    String endpoint = LocalPostgres.host() + ":" + LocalPostgres.port();
    if (LocalPostgres.isReachable()) {
      return ConditionEvaluationResult.enabled("本地 PG 可达（" + endpoint + "），执行持久化集成测试");
    }
    return ConditionEvaluationResult.disabled(
        "本地 PG 不可达（" + endpoint + "），跳过持久化集成测试——起 docker compose 项目 ishome-dev 后实跑");
  }
}
