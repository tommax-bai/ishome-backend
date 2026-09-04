package com.ishome.project.interfaces.rest;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ishome.project.application.ProjectFindOrCreateResult;

/** contracts project.v1 {@code project_summary}。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProjectSummaryResponse(
    String projectId, String currentMilestone, String processVersion, boolean created) {
  static ProjectSummaryResponse from(ProjectFindOrCreateResult result) {
    return new ProjectSummaryResponse(
        result.projectId(), result.currentMilestone(), result.processVersion(), result.created());
  }
}
