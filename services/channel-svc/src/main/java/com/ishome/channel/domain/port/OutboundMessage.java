package com.ishome.channel.domain.port;

/**
 * 出站消息占位模型。TODO: contracts 仓 channel.v1 统一消息模型（text/image/card/quick_reply/audio） SDK
 * 发布后替换为生成类型，本占位随即删除——统一模型的唯一真源在 contracts。
 *
 * @param channelType 渠道类型（注册表值）
 * @param channelInstance 接入实例，格式 {type}:{instance-slug}（规范 §6.1）
 * @param externalUserId 渠道内用户标识（通用名，不用渠道方言字段名——规范 §6.4）
 * @param contentType 五类消息之一：text/image/card/quick_reply/audio
 * @param payloadJson 消息载荷（统一模型 JSON）
 */
public record OutboundMessage(
    String channelType,
    String channelInstance,
    String externalUserId,
    String contentType,
    String payloadJson) {}
