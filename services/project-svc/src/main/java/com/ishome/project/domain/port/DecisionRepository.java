package com.ishome.project.domain.port;

import com.ishome.project.domain.Decision;
import java.util.List;

/** 用户决策记录仓储 port。 */
public interface DecisionRepository {

  void save(Decision decision);

  List<Decision> listByProjectId(String projectId);
}
