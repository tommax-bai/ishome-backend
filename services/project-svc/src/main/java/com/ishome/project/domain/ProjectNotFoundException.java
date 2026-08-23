package com.ishome.project.domain;

/** get 语义（必得）取不到项目时抛出（规范 §三：get=必得，find=可空）。 */
public class ProjectNotFoundException extends RuntimeException {

  public ProjectNotFoundException(String projectId) {
    super("项目不存在：" + projectId);
  }
}
