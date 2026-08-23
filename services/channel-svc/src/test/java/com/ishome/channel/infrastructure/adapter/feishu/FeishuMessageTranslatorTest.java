package com.ishome.channel.infrastructure.adapter.feishu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishome.channel.v1.AudioContent;
import com.ishome.channel.v1.CardContent;
import com.ishome.channel.v1.MessageDirection;
import com.ishome.channel.v1.QuickReplyContent;
import com.ishome.channel.v1.QuickReplyOption;
import com.ishome.channel.v1.TextContent;
import com.ishome.channel.v1.UnifiedMessage;
import com.ishome.common.v1.ChannelType;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 翻译纯函数单测：不依赖真实飞书连接（对齐 §6.7 消息映射的可测执行）。 */
class FeishuMessageTranslatorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void translatesInboundTextMessage() {
    Optional<UnifiedMessage> message =
        FeishuMessageTranslator.toInboundMessage(
            "ou_123", "om_456", "text", "{\"text\":\"你好，设计我的家\"}", 1_724_400_000_123L);

    assertTrue(message.isPresent());
    assertEquals("你好，设计我的家", message.get().getText().getText());
    assertEquals(ChannelType.CHANNEL_TYPE_FEISHU, message.get().getChannelType());
    assertEquals(MessageDirection.MESSAGE_DIRECTION_INBOUND, message.get().getDirection());
    assertEquals("ou_123", message.get().getExternalUserId());
    assertEquals(1_724_400_000L, message.get().getOccurredAt().getSeconds());
    // message_id 必须复用飞书原生消息 id：事件重推去重的关键（2026-08-23 真机事故回归断言）
    assertEquals("om_456", message.get().getMessageId());
    // 渠道方言只进 raw_payload 存档
    assertEquals(
        "om_456", message.get().getRawPayload().getFieldsOrThrow("message_id").getStringValue());
  }

  @Test
  void translatesInboundImageToSchemedUrl() {
    Optional<UnifiedMessage> message =
        FeishuMessageTranslator.toInboundMessage(
            "ou_123", "om_789", "image", "{\"image_key\":\"img_v2_abc\"}", 0L);

    assertTrue(message.isPresent());
    assertEquals(
        FeishuMessageTranslator.FEISHU_IMAGE_SCHEME + "img_v2_abc",
        message.get().getImage().getImageUrl());
  }

  @Test
  void skipsUnsupportedInboundType() {
    assertTrue(
        FeishuMessageTranslator.toInboundMessage("ou_123", "om_1", "sticker", "{}", 0L).isEmpty());
  }

  @Test
  void translatesCardButtonCallbackToSelectedOption() {
    UnifiedMessage message =
        FeishuMessageTranslator.toSelectedOption("evt_001", "ou_123", "opt-confirm", 0L);

    assertEquals("opt-confirm", message.getQuickReply().getSelectedOptionId());
    assertEquals(MessageDirection.MESSAGE_DIRECTION_INBOUND, message.getDirection());
    // message_id 复用回调事件 event_id（重推去重）
    assertEquals("evt_001", message.getMessageId());
  }

  @Test
  void mintsUlidWhenChannelMessageIdMissing() {
    UnifiedMessage message = FeishuMessageTranslator.toSelectedOption("", "ou_123", "opt-x", 0L);

    assertTrue(message.getMessageId().length() > 0);
  }

  @Test
  void translatesOutboundText() throws Exception {
    FeishuOutboundMessage outbound =
        FeishuMessageTranslator.toOutboundMessage(
            outboundBuilder().setText(TextContent.newBuilder().setText("三张方案图好了").build()).build());

    assertEquals("text", outbound.msgType());
    assertEquals("ou_123", outbound.receiveId());
    assertEquals("三张方案图好了", MAPPER.readTree(outbound.contentJson()).path("text").asText());
  }

  @Test
  void translatesOutboundCardToInteractive() throws Exception {
    FeishuOutboundMessage outbound =
        FeishuMessageTranslator.toOutboundMessage(
            outboundBuilder()
                .setCard(
                    CardContent.newBuilder()
                        .setTitle("你的初步方案")
                        .setDescription("点击查看交付图集")
                        .setLinkUrl("https://example.com/p/1")
                        .build())
                .build());

    assertEquals("interactive", outbound.msgType());
    JsonNode card = MAPPER.readTree(outbound.contentJson());
    assertEquals("你的初步方案", card.path("header").path("title").path("content").asText());
    assertEquals(
        "https://example.com/p/1",
        card.path("elements").get(1).path("actions").get(0).path("url").asText());
  }

  @Test
  void translatesQuickReplyToButtonsCarryingOptionId() throws Exception {
    FeishuOutboundMessage outbound =
        FeishuMessageTranslator.toOutboundMessage(
            outboundBuilder()
                .setQuickReply(
                    QuickReplyContent.newBuilder()
                        .setPromptText("这样确认吗？")
                        .addOptions(
                            QuickReplyOption.newBuilder().setOptionId("opt-yes").setLabel("确认"))
                        .addOptions(
                            QuickReplyOption.newBuilder().setOptionId("opt-no").setLabel("有问题"))
                        .build())
                .build());

    JsonNode actions =
        MAPPER.readTree(outbound.contentJson()).path("elements").get(1).path("actions");
    assertEquals("opt-yes", actions.get(0).path("value").path("option_id").asText());
    assertEquals("有问题", actions.get(1).path("text").path("content").asText());
  }

  @Test
  void rejectsUnsupportedOutboundAudio() {
    UnifiedMessage audio =
        outboundBuilder().setAudio(AudioContent.newBuilder().setAudioUrl("oss://a.ogg")).build();
    assertThrows(
        IllegalArgumentException.class, () -> FeishuMessageTranslator.toOutboundMessage(audio));
  }

  private static UnifiedMessage.Builder outboundBuilder() {
    return UnifiedMessage.newBuilder()
        .setMessageId("01TESTULID")
        .setChannelType(ChannelType.CHANNEL_TYPE_FEISHU)
        .setDirection(MessageDirection.MESSAGE_DIRECTION_OUTBOUND)
        .setExternalUserId("ou_123");
  }
}
