package com.ishome.channel.infrastructure.persistence;

/** svc_channel.outbound_messages 持久化对象：idempotency_key 空白发送记 NULL（不参与防重）。 */
public class OutboundMessagePO {

  private String id;
  private String idempotencyKey;
  private String messageId;
  private String channelType;
  private String channelInstance;
  private String externalUserId;
  private String channelMessageId;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public String getMessageId() {
    return messageId;
  }

  public void setMessageId(String messageId) {
    this.messageId = messageId;
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

  public String getExternalUserId() {
    return externalUserId;
  }

  public void setExternalUserId(String externalUserId) {
    this.externalUserId = externalUserId;
  }

  public String getChannelMessageId() {
    return channelMessageId;
  }

  public void setChannelMessageId(String channelMessageId) {
    this.channelMessageId = channelMessageId;
  }
}
