package com.ishome.channel.application;

import com.ishome.channel.domain.ChannelAdapterRegistry;
import com.ishome.channel.domain.port.ChannelAdapter;
import com.ishome.channel.v1.MessageDirection;
import com.ishome.channel.v1.UnifiedMessage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 出站发送用例：SendMessage 按 channel_type 路由 adapter，幂等键防重发（IM 消息不可撤回，重复代价高）。
 *
 * <p>幂等实现为最小形态（进程内存）；TODO(durability)：随 svc_channel 首个持久化用例换持久幂等表。
 */
@Service
public class OutboundMessageAppService {

  private final ChannelAdapterRegistry channelAdapterRegistry;
  private final Map<String, OutboundSendResult> sentByIdempotencyKey = new ConcurrentHashMap<>();

  public OutboundMessageAppService(ChannelAdapterRegistry channelAdapterRegistry) {
    this.channelAdapterRegistry = channelAdapterRegistry;
  }

  public OutboundSendResult send(UnifiedMessage message, String idempotencyKey) {
    if (message.getDirection() != MessageDirection.MESSAGE_DIRECTION_OUTBOUND) {
      throw new IllegalArgumentException("send requires direction=OUTBOUND");
    }
    if (!idempotencyKey.isBlank()) {
      OutboundSendResult sent = sentByIdempotencyKey.get(idempotencyKey);
      if (sent != null) {
        return sent;
      }
    }
    ChannelAdapter adapter = channelAdapterRegistry.getAdapter(message.getChannelType());
    String channelMessageId = adapter.send(message);
    OutboundSendResult result = new OutboundSendResult(message.getMessageId(), channelMessageId);
    if (!idempotencyKey.isBlank()) {
      sentByIdempotencyKey.putIfAbsent(idempotencyKey, result);
    }
    return result;
  }
}
