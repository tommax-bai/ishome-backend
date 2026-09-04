package com.ishome.project.interfaces.rest;

import com.ishome.project.application.ProjectAppService;
import com.ishome.project.domain.GenerationTaskNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生成任务结果回流口（contracts project.v1 {@code POST /generation-tasks/{task_id}/result}）：编排侧按派发时注入的
 * 回调地址把结论送来。路径里的 task_id 为准，body 里的只作核对。
 */
@RestController
@RequestMapping("/api/v1/generation-tasks")
public class GenerationTasksController {
  private final ProjectAppService projectAppService;

  public GenerationTasksController(ProjectAppService projectAppService) {
    this.projectAppService = projectAppService;
  }

  @PostMapping("/{taskId}/result")
  public GenerationTaskResultResponse deliverResult(
      @PathVariable("taskId") String taskId, @RequestBody GenerationTaskResultRequest request) {
    return GenerationTaskResultResponse.from(
        projectAppService.receiveGenerationTaskResult(request.toCommand(taskId)));
  }

  @ExceptionHandler(GenerationTaskNotFoundException.class)
  public ResponseEntity<Map<String, String>> onTaskNotFound(GenerationTaskNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", e.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> onBadRequest(IllegalArgumentException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("detail", e.getMessage()));
  }
}
