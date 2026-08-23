package com.ishome.channel.domain;

/** 出站发送记录事实：幂等键命中时以此复答，不再触达渠道（IM 消息不可撤回）。 */
public record OutboundSendRecord(String messageId, String channelMessageId) {}
