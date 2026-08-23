package com.ishome.channel.application;

/**
 * 出站发送用例出参（application 层 Result，规范 §2.2）。
 *
 * @param messageId 统一模型侧 message_id（ULID）
 * @param channelMessageId 渠道侧消息 id（方言值，仅存档用）
 */
public record OutboundSendResult(String messageId, String channelMessageId) {}
