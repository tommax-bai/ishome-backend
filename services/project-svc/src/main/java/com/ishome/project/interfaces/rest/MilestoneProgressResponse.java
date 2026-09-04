package com.ishome.project.interfaces.rest;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ishome.project.application.MilestoneProgressResult;
import java.util.List;

/** contracts project.v1 {@code milestone_progress}。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MilestoneProgressResponse(
    String projectId,
    String currentMilestone,
    boolean advanced,
    List<String> enteredMilestones,
    List<String> createdTaskIds) {
  static MilestoneProgressResponse from(MilestoneProgressResult result) {
    return new MilestoneProgressResponse(
        result.projectId(),
        result.currentMilestone(),
        result.transitioned(),
        result.enteredMilestones(),
        result.createdTaskIds());
  }
}
