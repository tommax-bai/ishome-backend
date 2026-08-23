package com.ishome.project.domain.port;

import com.ishome.project.domain.RevisionLog;

/** 修订记录仓储 port——countByProjectIdAndMilestone 即修订预算判定的已用轮数。 */
public interface RevisionLogRepository {

  void save(RevisionLog revisionLog);

  int countByProjectIdAndMilestone(String projectId, String milestone);
}
