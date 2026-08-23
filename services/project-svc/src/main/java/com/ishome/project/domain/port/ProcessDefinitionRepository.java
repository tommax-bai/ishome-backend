package com.ishome.project.domain.port;

import com.ishome.project.domain.ProcessDefinitionNotFoundException;
import com.ishome.project.domain.definition.ProcessDefinition;
import java.util.Optional;

/** 流程定义仓储 port。定义是版本化配置制品（归业务域、随 monorepo 评审），当前实现为内置数据； project-svc 是其权威分发点（D10）。 */
public interface ProcessDefinitionRepository {

  Optional<ProcessDefinition> findByVersion(String version);

  default ProcessDefinition getByVersion(String version) {
    return findByVersion(version)
        .orElseThrow(() -> new ProcessDefinitionNotFoundException(version));
  }
}
