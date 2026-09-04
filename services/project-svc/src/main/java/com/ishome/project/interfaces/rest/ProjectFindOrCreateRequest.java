package com.ishome.project.interfaces.rest;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ishome.project.domain.ProjectOwner;

/** contracts project.v1 {@code project_find_or_create_request}（snake_case）。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProjectFindOrCreateRequest(ConversationOwnerRequest owner, String processVersion) {
  ProjectOwner toOwner() {
    if (owner == null) {
      throw new IllegalArgumentException("缺 owner：不知道这是谁的项目");
    }
    return owner.toDomain();
  }
}
