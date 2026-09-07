package com.ishome.project.interfaces.rest;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ishome.project.application.ProjectFindOrCreateResult;
import java.util.List;

/** contracts project.v1 {@code project_summary}：项目定位 + 项目上已有的全部槽位（会话侧的读面）。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProjectSummaryResponse(
    String projectId,
    String currentMilestone,
    String processVersion,
    boolean created,
    List<ProjectSlotResponse> slots) {
  static ProjectSummaryResponse from(ProjectFindOrCreateResult result) {
    return new ProjectSummaryResponse(
        result.projectId(),
        result.currentMilestone(),
        result.processVersion(),
        result.created(),
        result.slots().stream().map(ProjectSlotResponse::from).toList());
  }
}
