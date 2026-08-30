package com.ishome.project.testsupport;

import com.ishome.project.domain.port.ReleaseRepository;
import com.ishome.project.domain.rulebook.ReleaseSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** release 快照的内存假实现：求值线上游只需要"这个域最新那一份"，测试里直接摆好。 */
public class InMemoryReleaseRepository implements ReleaseRepository {

  private final Map<String, ReleaseSnapshot> latest = new LinkedHashMap<>();

  public InMemoryReleaseRepository with(ReleaseSnapshot snapshot) {
    latest.put(snapshot.domain(), snapshot);
    return this;
  }

  @Override
  public Optional<ReleaseSnapshot> findLatest(String domain) {
    return Optional.ofNullable(latest.get(domain));
  }
}
