package com.ishome.channel.application;

import com.ishome.channel.domain.port.DesignConversationGateway;
import com.ishome.channel.domain.port.InboundMessageRecordRepository;
import com.ishome.channel.domain.port.InboundMessageRelay;
import com.ishome.channel.v1.MessageDirection;
import com.ishome.channel.v1.UnifiedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 入站消息用例：渠道 adapter → 幂等去重（svc_channel.inbound_messages 唯一键）→ 统一模型 → design-svc 会话入口。
 *
 * <p>去重先于中继：渠道事件重推（飞书处理超时重投）在此拦截，同一条用户消息只中继一次。 输入归一化（语音转文字、多消息聚合）不在这里——渠道层不理解语义，归一化归 design-svc（对齐
 * §6.6）。
 */
@Service
public class InboundMessageAppService implements InboundMessageRelay {

  private static final Logger log = LoggerFactory.getLogger(InboundMessageAppService.class);

  private final DesignConversationGateway designConversationGateway;
  private final InboundMessageRecordRepository inboundMessageRecordRepository;

  public InboundMessageAppService(
      DesignConversationGateway designConversationGateway,
      InboundMessageRecordRepository inboundMessageRecordRepository) {
    this.designConversationGateway = designConversationGateway;
    this.inboundMessageRecordRepository = inboundMessageRecordRepository;
  }

  @Override
  public String relay(UnifiedMessage message) {
    if (message.getDirection() != MessageDirection.MESSAGE_DIRECTION_INBOUND) {
      throw new IllegalArgumentException("inbound relay requires direction=INBOUND");
    }
    if (!inboundMessageRecordRepository.recordIfFirstSeen(message)) {
      log.info(
          "inbound duplicate skipped: channel={}/{} message_id={}",
          message.getChannelType(),
          message.getChannelInstance(),
          message.getMessageId());
      return message.getMessageId();
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
