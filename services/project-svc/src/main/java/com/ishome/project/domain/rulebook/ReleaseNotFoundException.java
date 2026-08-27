package com.ishome.project.domain.rulebook;

/** 域无任何 release 时抛出：运行时只读 release（规则 4.12），未发布的域不可求值——冷启动先发布再消费。 */
public class ReleaseNotFoundException extends RuntimeException {

  public ReleaseNotFoundException(String domain) {
    super("域 " + domain + " 无 release 快照：先 publish_release.py 发布（规则 4.12 运行时只读 release）");
  }
}
