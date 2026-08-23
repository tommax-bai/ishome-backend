package com.ishome.channel.infrastructure.adapter.feishu;

/**
 * 统一模型翻译后的飞书出站形态（渠道方言，仅存在于本 adapter 包内）。
 *
 * @param receiveId 接收方 open_id
 * @param msgType 飞书 msg_type（text / image / interactive）
 * @param contentJson 飞书消息体 JSON
 */
public record FeishuOutboundMessage(String receiveId, String msgType, String contentJson) {}
