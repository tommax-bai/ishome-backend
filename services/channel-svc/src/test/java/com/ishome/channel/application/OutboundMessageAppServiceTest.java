package com.ishome.channel.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ishome.channel.domain.ChannelAdapterRegistry;
import com.ishome.channel.domain.UnknownChannelException;
import com.ishome.channel.domain.port.ChannelAdapter;
import com.ishome.channel.v1.ChannelCapability;
import com.ishome.channel.v1.MessageDirection;
import com.ishome.channel.v1.UnifiedMessage;
import com.ishome.common.v1.ChannelType;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OutboundMessageAppServiceTest {

  private final AtomicInteger sendCount = new AtomicInteger();

  private final ChannelAdapter countingAdapter =
      new ChannelAdapter() {
        @Override
        public ChannelType channelType() {
          return ChannelType.CHANNEL_TYPE_MOCK;
        }

        @Override
        public ChannelCapability capability() {
          return ChannelCapability.getDefaultInstance();
        }

        @Override
        public String send(UnifiedMessage message) {
          return "ch-" + sendCount.incrementAndGet();
        }
      };

  private final OutboundMessageAppService service =
      new OutboundMessageAppService(new ChannelAdapterRegistry(List.of(countingAdapter)));

  @Test
  void idempotencyKeyPreventsDuplicateSend() {
    UnifiedMessage message = outboundMessage();

    OutboundSendResult first = service.send(message, "idem-1");
    OutboundSendResult second = service.send(message, "idem-1");

    assertEquals(1, sendCount.get());
    assertEquals(first, second);
  }

  @Test
  void blankIdempotencyKeySendsEveryTime() {
    service.send(outboundMessage(), "");
    service.send(outboundMessage(), "");
    assertEquals(2, sendCount.get());
  }

  @Test
  void rejectsInboundDirection() {
    UnifiedMessage inbound =
        outboundMessage().toBuilder()
            .setDirection(MessageDirection.MESSAGE_DIRECTION_INBOUND)
            .build();
    assertThrows(IllegalArgumentException.class, () -> service.send(inbound, "k"));
  }

  @Test
  void unknownChannelTypeThrows() {
    UnifiedMessage feishuBound =
        outboundMessage().toBuilder().setChannelType(ChannelType.CHANNEL_TYPE_FEISHU).build();
    assertThrows(UnknownChannelException.class, () -> service.send(feishuBound, "k"));
  }

  private static UnifiedMessage outboundMessage() {
    return UnifiedMessage.newBuilder()
        .setMessageId("01TESTULID")
        .setChannelType(ChannelType.CHANNEL_TYPE_MOCK)
        .setDirection(MessageDirection.MESSAGE_DIRECTION_OUTBOUND)
        .setExternalUserId("u-1")
        .build();
  }
}
