package com.ishome.shared.kernel.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/** 持久化集成测试条件：本地 PG（{@link LocalPostgres}）可达才执行，不可达跳过而非红—— 本地未起 docker compose 或 CI 无 PG 时构建保持绿。 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(LocalPostgresAvailableCondition.class)
public @interface EnabledIfLocalPostgres {}
