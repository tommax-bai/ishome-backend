package com.ishome.project.interfaces.rest;

import com.ishome.project.application.ProjectAppService;
import com.ishome.project.domain.ProjectNotFoundException;
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
 * 业务事实入口（contracts openapi/project.v1.yaml）：会话侧把事实报到这里，里程碑判定与派发都在本服务内发生。
 *
 * <p>chat 不判里程碑、不建任务——它只做两件事：把事实上报（本控制器）、把产物呈现（design.v1 PresentDeliverables）。
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectsController {
  private final ProjectAppService projectAppService;

  public ProjectsController(ProjectAppService projectAppService) {
    this.projectAppService = projectAppService;
  }

  /** 按会话属主取项目，没有就建（幂等）。 */
  @PostMapping
  public ProjectSummaryResponse findOrCreate(@RequestBody ProjectFindOrCreateRequest request) {
    return ProjectSummaryResponse.from(
        projectAppService.findOrCreateProject(request.toOwner(), request.processVersion()));
  }

  /** 一批 slot_filled：同事务落完再判一次里程碑。 */
  @PostMapping("/{projectId}/slots")
  public MilestoneProgressResponse fillSlots(
      @PathVariable("projectId") String projectId, @RequestBody SlotsFillRequest request) {
    return MilestoneProgressResponse.from(
        projectAppService.fillSlots(projectId, request.toCommands(projectId)));
  }

  @ExceptionHandler(ProjectNotFoundException.class)
  public ResponseEntity<Map<String, String>> onProjectNotFound(ProjectNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", e.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> onBadRequest(IllegalArgumentException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("detail", e.getMessage()));
  }
}
