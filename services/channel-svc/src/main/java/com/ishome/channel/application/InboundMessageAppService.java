package com.ishome.channel.application;

import com.ishome.channel.domain.port.DesignConversationGateway;
import com.ishome.channel.domain.port.InboundMessageRelay;
import com.ishome.channel.v1.MessageDirection;
import com.ishome.channel.v1.UnifiedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 入站消息用例：渠道 adapter → 统一模型 → design-svc 会话入口。
 *
 * <p>输入归一化（语音转文字、多消息聚合）不在这里——渠道层不理解语义，归一化归 design-svc（对齐 §6.6）。
 */
@Service
public class InboundMessageAppService implements InboundMessageRelay {

  private static final Logger log = LoggerFactory.getLogger(InboundMessageAppService.class);

  private final DesignConversationGateway designConversationGateway;

  public InboundMessageAppService(DesignConversationGateway designConversationGateway) {
    this.designConversationGateway = designConversationGateway;
  }

  @Override
  public String relay(UnifiedMessage message) {
    if (message.getDirection() != MessageDirection.MESSAGE_DIRECTION_INBOUND) {
      throw new IllegalArgumentException("inbound relay requires direction=INBOUND");
    }
    // TODO(identity)：external_user_id → identity-svc 渠道绑定，归一 user_id（对齐 §6.5）
    // TODO(events)：发布 CloudEvents channel.message.received（outbox，RocketMQ 接入后）
    String ackId = designConversationGateway.ingest(message);
    log.info(
        "inbound relayed to design: channel={}/{} message_id={} content={} ack={}",
        message.getChannelType(),
        message.getChannelInstance(),
        message.getMessageId(),
        message.getContentCase(),
        ackId);
    return ackId;
  }
}
