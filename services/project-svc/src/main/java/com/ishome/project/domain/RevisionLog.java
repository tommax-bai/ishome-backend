package com.ishome.project.domain;

/** 修订记录（对齐文档 §5.1 svc_project.revision_log）——修订预算判定的依据： 同项目同里程碑的记录数 = 已用修订轮数。 */
public record RevisionLog(
    String projectId, String milestone, int roundNo, RevisionDirective directive, String taskId) {}
