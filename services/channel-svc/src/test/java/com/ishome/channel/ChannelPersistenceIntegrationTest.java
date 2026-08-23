package com.ishome.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.f4b6a3.ulid.UlidCreator;
import com.ishome.channel.domain.OutboundSendRecord;
import com.ishome.channel.domain.port.InboundMessageRecordRepository;
import com.ishome.channel.domain.port.OutboundSendRecordRepository;
import com.ishome.channel.testsupport.PostgresIntegrationTestSupport;
import com.ishome.channel.v1.MessageDirection;
import com.ishome.channel.v1.TextContent;
import com.ishome.channel.v1.UnifiedMessage;
import com.ishome.common.v1.ChannelType;
import com.ishome.shared.kernel.testsupport.EnabledIfLocalPostgres;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 收发幂等仓储 PG 实跑（Flyway 迁移 + MyBatis 实现，独立 schema svc_channel_it）：本地 PG 可达才执行，不可达跳过。 */
@SpringBootTest
@EnabledIfLocalPostgres
@Import(PostgresIntegrationTestSupport.CleanMigrateConfig.class)
class ChannelPersistenceIntegrationTest {

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    PostgresIntegrationTestSupport.register(registry);
    registry.add("ishome.channel.grpc-port", () -> 0);
  }

  @Autowired InboundMessageRecordRepository inboundMessageRecordRepository;
  @Autowired OutboundSendRecordRepository outboundSendRecordRepository;

  /** 入站唯一键 (channel_type, channel_instance, 渠道原生消息 id)：重推只首见一次。 */
  @Test
  void inboundDuplicateIsRejectedBySameChannelMessageKey() {
    String channelMessageId = "om_" + UlidCreator.getUlid();
    UnifiedMessage message = inboundMessage(channelMessageId);

    assertTrue(inboundMessageRecordRepository.recordIfFirstSeen(message));
    assertFalse(inboundMessageRecordRepository.recordIfFirstSeen(message));
    // 渠道原生 id 不同 = 新消息
    assertTrue(inboundMessageRecordRepository.recordIfFirstSeen(inboundMessage("om_other")));
  }

  /** 出站幂等键：落记录后按键可查回同一 channel_message_id；空白键仅留痕不参与防重。 */
  @Test
  void outboundIdempotencyKeyRoundTrip() {
    String idempotencyKey = "idem-" + UlidCreator.getUlid();
    UnifiedMessage message = outboundMessage("01OUTULID1");

    outboundSendRecordRepository.recordSent(message, idempotencyKey, "ch-msg-1");
    Optional<OutboundSendRecord> sent =
        outboundSendRecordRepository.findByIdempotencyKey(idempotencyKey);
    assertTrue(sent.isPresent());
    assertEquals("01OUTULID1", sent.get().messageId());
    assertEquals("ch-msg-1", sent.get().channelMessageId());

    // 并发重复写：唯一约束下首写胜出，不抛错
    outboundSendRecordRepository.recordSent(message, idempotencyKey, "ch-msg-2");
    assertEquals(
        "ch-msg-1",
        outboundSendRecordRepository.findByIdempotencyKey(idempotencyKey).get().channelMessageId());

    // 空白键：审计留痕、不建幂等真相
    outboundSendRecordRepository.recordSent(outboundMessage("01OUTULID2"), "", "ch-msg-3");
    assertTrue(outboundSendRecordRepository.findByIdempotencyKey("").isEmpty());
  }

  private static UnifiedMessage inboundMessage(String channelMessageId) {
    return UnifiedMessage.newBuilder()
        .setMessageId(channelMessageId)
        .setChannelType(ChannelType.CHANNEL_TYPE_MOCK)
        .setChannelInstance("mock:local")
        .setDirection(MessageDirection.MESSAGE_DIRECTION_INBOUND)
        .setExternalUserId("u-it")
        .setText(TextContent.newBuilder().setText("hello"))
        .build();
  }

  private static UnifiedMessage outboundMessage(String messageId) {
    return UnifiedMessage.newBuilder()
        .setMessageId(messageId)
        .setChannelType(ChannelType.CHANNEL_TYPE_MOCK)
        .setChannelInstance("mock:local")
        .setDirection(MessageDirection.MESSAGE_DIRECTION_OUTBOUND)
        .setExternalUserId("u-it")
        .setText(TextContent.newBuilder().setText("world"))
        .build();
  }
}
