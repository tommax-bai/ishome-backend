package com.ishome.project.application;

import com.ishome.project.domain.definition.ProcessDefinition;
import com.ishome.project.domain.port.ProcessDefinitionRepository;
import org.springframework.stereotype.Service;

/** 流程定义分发用例（D10 单一来源双消费）：project-svc 权威分发，chat 拉取+缓存不落库。 读操作，无事务边界。 */
@Service
public class ProcessDefinitionAppService {

  private final ProcessDefinitionRepository processDefinitionRepository;

  public ProcessDefinitionAppService(ProcessDefinitionRepository processDefinitionRepository) {
    this.processDefinitionRepository = processDefinitionRepository;
  }

  public ProcessDefinition getDefinition(String version) {
    return processDefinitionRepository.getByVersion(version);
  }
}
