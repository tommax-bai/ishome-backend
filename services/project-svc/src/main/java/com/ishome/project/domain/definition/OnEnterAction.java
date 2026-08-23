package com.ishome.project.domain.definition;

import java.util.Map;

/**
 * 进入里程碑时执行的动作声明——数据，不是逻辑：执行编排在 application 层 （如 CREATE_TASK → 落 generation_task + 启动 genpipe
 * workflow）。
 */
public record OnEnterAction(OnEnterActionType type, Map<String, String> params) {

  public static final String PARAM_TASK_TYPE = "task_type";

  public static OnEnterAction createTask(String taskType) {
    return new OnEnterAction(OnEnterActionType.CREATE_TASK, Map.of(PARAM_TASK_TYPE, taskType));
  }
}
