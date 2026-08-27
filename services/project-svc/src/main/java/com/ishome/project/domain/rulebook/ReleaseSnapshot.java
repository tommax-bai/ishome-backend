package com.ishome.project.domain.rulebook;

import java.util.List;

/**
 * 域级 release 不可变快照（规则 4.12）在求值线的读取投影：首实装只投影 parameters 与 personas，
 * 其余形态（rules/checks/attributes/…）随清单求值与机检落地时扩展投影，不动快照本体。
 */
public record ReleaseSnapshot(
    String domain,
    String releaseTag,
    List<ParameterAsset> parameters,
    List<PersonaAssetRef> personas) {

  public ReleaseRef ref() {
    return new ReleaseRef(domain, releaseTag);
  }
}
