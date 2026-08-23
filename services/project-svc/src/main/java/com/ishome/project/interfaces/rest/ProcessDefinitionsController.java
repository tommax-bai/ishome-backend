package com.ishome.project.interfaces.rest;

import com.ishome.project.application.ProcessDefinitionAppService;
import com.ishome.project.domain.ProcessDefinitionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程定义权威分发（D10 单一来源双消费）：chat 拉取消费槽位 schema/修订维度词表/动作白名单， project 自身消费判据/on_enter
 * 编排/修订预算。整份定义单点分发，消费方各取切片。
 */
@RestController
@RequestMapping("/api/v1/process-definitions")
public class ProcessDefinitionsController {

  private final ProcessDefinitionAppService processDefinitionAppService;

  public ProcessDefinitionsController(ProcessDefinitionAppService processDefinitionAppService) {
    this.processDefinitionAppService = processDefinitionAppService;
  }

  @GetMapping("/{version}")
  public ProcessDefinitionResponse getDefinition(@PathVariable("version") String version) {
    return ProcessDefinitionResponse.from(processDefinitionAppService.getDefinition(version));
  }

  @ExceptionHandler(ProcessDefinitionNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public void onProcessDefinitionNotFound() {
    // 404，无响应体：版本号是闭集数据，未命中即不存在
  }
}
