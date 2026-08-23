package com.ishome.channel.application;

import com.ishome.channel.domain.ChannelAdapterRegistry;
import com.ishome.channel.domain.port.ChannelAdapter;
import com.ishome.channel.domain.port.OutboundSendRecordRepository;
import com.ishome.channel.v1.MessageDirection;
import com.ishome.channel.v1.UnifiedMessage;
import org.springframework.stereotype.Service;

/**
 * 出站发送用例：SendMessage 按 channel_type 路由 adapter，幂等键防重发（IM 消息不可撤回，重复代价高）。
 *
 * <p>幂等真相在 svc_channel.outbound_messages（幂等键唯一约束）：键命中即以既有记录复答，不再触达渠道； 空白键的发送仅审计留痕、每次实发。
 */
@Service
public class OutboundMessageAppService {

  private final ChannelAdapterRegistry channelAdapterRegistry;
  private final OutboundSendRecordRepository outboundSendRecordRepository;

  public OutboundMessageAppService(
      ChannelAdapterRegistry channelAdapterRegistry,
      OutboundSendRecordRepository outboundSendRecordRepository) {
    this.channelAdapterRegistry = channelAdapterRegistry;
    this.outboundSendRecordRepository = outboundSendRecordRepository;
  }

  public OutboundSendResult send(UnifiedMessage message, String idempotencyKey) {
    if (message.getDirection() != MessageDirection.MESSAGE_DIRECTION_OUTBOUND) {
      throw new IllegalArgumentException("send requires direction=OUTBOUND");
    }
    if (!idempotencyKey.isBlank()) {
      var sent = outboundSendRecordRepository.findByIdempotencyKey(idempotencyKey);
      if (sent.isPresent()) {
        return new OutboundSendResult(sent.get().messageId(), sent.get().channelMessageId());
      }
    }
    ChannelAdapter adapter = channelAdapterRegistry.getAdapter(message.getChannelType());
    String channelMessageId = adapter.send(message);
    outboundSendRecordRepository.recordSent(message, idempotencyKey, channelMessageId);
    return new OutboundSendResult(message.getMessageId(), channelMessageId);
  }
}
