package com.ishome.channel.infrastructure.persistence;

import java.time.OffsetDateTime;

/** svc_channel.inbound_messages 持久化对象：channel_message_id = 渠道原生消息 id。 */
public class InboundMessagePO {

  private String id;
  private String channelType;
  private String channelInstance;
  private String channelMessageId;
  private String externalUserId;
  private OffsetDateTime occurredAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getChannelType() {
    return channelType;
  }

  public void setChannelType(String channelType) {
    this.channelType = channelType;
  }

  public String getChannelInstance() {
    return channelInstance;
  }

  public void setChannelInstance(String channelInstance) {
    this.channelInstance = channelInstance;
  }

  public String getChannelMessageId() {
    return channelMessageId;
  }

  public void setChannelMessageId(String channelMessageId) {
    this.channelMessageId = channelMessageId;
  }

  public String getExternalUserId() {
    return externalUserId;
  }

  public void setExternalUserId(String externalUserId) {
    this.externalUserId = externalUserId;
  }

  public OffsetDateTime getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(OffsetDateTime occurredAt) {
    this.occurredAt = occurredAt;
  }
}
