package com.ishome.project.infrastructure.persistence;

/** svc_rulebook.releases 持久化对象：snapshot 以 jsonb 文本读出，解码在 RepositoryImpl（domain 不见 JSON 库）。 */
public class ReleasePO {

  private String id;
  private String domain;
  private String releaseTag;
  private String snapshot;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getDomain() {
    return domain;
  }

  public void setDomain(String domain) {
    this.domain = domain;
  }

  public String getReleaseTag() {
    return releaseTag;
  }

  public void setReleaseTag(String releaseTag) {
    this.releaseTag = releaseTag;
  }

  public String getSnapshot() {
    return snapshot;
  }

  public void setSnapshot(String snapshot) {
    this.snapshot = snapshot;
  }
}
