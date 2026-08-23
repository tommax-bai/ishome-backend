package com.ishome.project.domain;

/** get 语义（必得）取不到流程定义版本时抛出（接口层映射为 404）。 */
public class ProcessDefinitionNotFoundException extends RuntimeException {

  public ProcessDefinitionNotFoundException(String version) {
    super("流程定义版本不存在：" + version);
  }
}
