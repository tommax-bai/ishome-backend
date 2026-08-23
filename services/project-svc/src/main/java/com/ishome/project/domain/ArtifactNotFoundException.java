package com.ishome.project.domain;

/** get 语义（必得）取不到产物时抛出。 */
public class ArtifactNotFoundException extends RuntimeException {

  public ArtifactNotFoundException(String artifactId) {
    super("产物不存在：" + artifactId);
  }
}
