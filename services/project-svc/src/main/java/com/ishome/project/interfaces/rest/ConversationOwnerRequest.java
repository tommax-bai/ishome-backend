package com.ishome.project.interfaces.rest;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ishome.project.domain.ProjectOwner;

/** contracts project.v1 {@code conversation_owner}：channel_type 用注册表小写标识。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ConversationOwnerRequest(
    String channelType, String channelInstance, String externalUserId) {
  ProjectOwner toDomain() {
    return new ProjectOwner(channelType, channelInstance, externalUserId);
  }
}
