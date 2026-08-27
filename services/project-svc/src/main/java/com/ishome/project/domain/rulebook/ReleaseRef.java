package com.ishome.project.domain.rulebook;

/** release 引用（contracts rulebook/README §6）：{domain}@v{n}，产物 lineage 记录的版本锁定单元（规则 8.2）。 */
public record ReleaseRef(String domain, String releaseTag) {}
