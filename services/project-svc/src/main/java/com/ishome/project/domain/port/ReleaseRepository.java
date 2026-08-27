package com.ishome.project.domain.port;

import com.ishome.project.domain.rulebook.ReleaseSnapshot;
import java.util.Optional;

/** svc_rulebook.releases 读取 port：求值线唯一读取面（规则 4.12），只读最新——回滚以切 release_tag 表达。 */
public interface ReleaseRepository {

  Optional<ReleaseSnapshot> findLatest(String domain);
}
