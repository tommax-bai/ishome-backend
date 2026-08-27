package com.ishome.project.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * svc_rulebook.releases Mapper。schema 名经 MyBatis 配置变量 {@code ${rulebookSchema}} 注入 （application.yml
 * 正式=svc_rulebook；集成测试=svc_rulebook_it）——与 Flyway placeholder 同一事实两处注入。
 *
 * <p>最新版取 created_at 最大者：release 只 INSERT 不 UPDATE（不可变，规则 4.12），created_at 随版本号单调。
 */
@Mapper
public interface ReleaseMapper {

  @Select(
      "SELECT id, domain, release_tag, snapshot::text AS snapshot FROM ${rulebookSchema}.releases "
          + "WHERE domain = #{domain} ORDER BY created_at DESC, release_tag DESC LIMIT 1")
  ReleasePO findLatestByDomain(@Param("domain") String domain);
}
