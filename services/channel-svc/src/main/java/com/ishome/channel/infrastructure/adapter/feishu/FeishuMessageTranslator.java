package com.ishome.channel.infrastructure.adapter.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.f4b6a3.ulid.UlidCreator;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import com.ishome.channel.v1.ImageContent;
import com.ishome.channel.v1.MessageDirection;
import com.ishome.channel.v1.QuickReplyContent;
import com.ishome.channel.v1.TextContent;
import com.ishome.channel.v1.UnifiedMessage;
import com.ishome.common.v1.ChannelType;
import java.util.Optional;

/**
 * 统一消息模型 ↔ 飞书方言的双向翻译（对齐 §6.7 消息映射）。纯函数，单元测试不依赖真实连接。
 *
 * <p>text/image 直映射；card → 飞书交互卡片 JSON；quick_reply → 卡片按钮（value 带 option_id）， 按钮回调经 {@link
 * #toSelectedOption} 翻译回统一模型的"用户选择"消息。 飞书富卡片（输入框/下拉）超出五类基础模型，只经能力声明暴露，不进基础契约。
 */
final class FeishuMessageTranslator {

  static final String FEISHU_CHANNEL_INSTANCE = "feishu:ishome-prod";

  /** image_url 承载飞书 image_key 的暂定 scheme；媒体与 OSS 双向中转 TODO(media)。 */
  static final String FEISHU_IMAGE_SCHEME = "feishu-image://";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private FeishuMessageTranslator() {}

  /**
   * 入站：飞书消息事件 → 统一模型。不支持的 msg_type 返回 empty（调用方记日志跳过）。
   *
   * <p>message_id 直接采用飞书原生消息 id：事件重推（处理超时未及时 ack 时飞书会重投同一事件） 在 design-svc 幂等去重处必须命中同一
   * id，否则同一条用户消息会被回复多遍（2026-08-23 真机事故）。
   */
  static Optional<UnifiedMessage> toInboundMessage(
      String openId, String feishuMessageId, String msgType, String contentJson, long createdAtMs) {
    UnifiedMessage.Builder builder =
        inboundBuilder(feishuMessageId, openId, createdAtMs)
            .setRawPayload(
                Struct.newBuilder()
                    .putFields(
                        "message_id", Value.newBuilder().setStringValue(feishuMessageId).build())
                    .putFields("msg_type", Value.newBuilder().setStringValue(msgType).build())
                    .putFields("content", Value.newBuilder().setStringValue(contentJson).build())
                    .build());
    JsonNode content = readJson(contentJson);
    return switch (msgType) {
      case "text" ->
          Optional.of(
              builder
                  .setText(TextContent.newBuilder().setText(content.path("text").asText()).build())
                  .build());
      case "image" ->
          Optional.of(
              builder
                  .setImage(
                      ImageContent.newBuilder()
                          .setImageUrl(FEISHU_IMAGE_SCHEME + content.path("image_key").asText())
                          .build())
                  .build());
      default -> Optional.empty();
    };
  }

  /**
   * 入站：卡片按钮回调 → 统一模型"用户选择"消息（selected_option_id 仅入站方向使用）。
   *
   * <p>message_id 采用回调事件的 event_id（重推去重理由同上）。
   */
  static UnifiedMessage toSelectedOption(
      String eventId, String openId, String optionId, long createdAtMs) {
    return inboundBuilder(eventId, openId, createdAtMs)
        .setQuickReply(QuickReplyContent.newBuilder().setSelectedOptionId(optionId).build())
        .build();
  }

  /** 出站：统一模型 → 飞书发送形态。audio 暂不支持（能力声明如实缺省）。 */
  static FeishuOutboundMessage toOutboundMessage(UnifiedMessage message) {
    String receiveId = message.getExternalUserId();
    return switch (message.getContentCase()) {
      case TEXT -> {
        ObjectNode content = MAPPER.createObjectNode().put("text", message.getText().getText());
        yield new FeishuOutboundMessage(receiveId, "text", content.toString());
      }
      case IMAGE -> {
        String imageUrl = message.getImage().getImageUrl();
        String imageKey =
            imageUrl.startsWith(FEISHU_IMAGE_SCHEME)
                ? imageUrl.substring(FEISHU_IMAGE_SCHEME.length())
                : imageUrl;
        ObjectNode content = MAPPER.createObjectNode().put("image_key", imageKey);
        yield new FeishuOutboundMessage(receiveId, "image", content.toString());
      }
      case CARD -> {
        ObjectNode card = cardSkeleton();
        card.putObject("header")
            .putObject("title")
            .put("tag", "plain_text")
            .put("content", message.getCard().getTitle());
        ArrayNode elements = card.putArray("elements");
        elements
            .addObject()
            .put("tag", "div")
            .putObject("text")
            .put("tag", "lark_md")
            .put("content", message.getCard().getDescription());
        ArrayNode actions = elements.addObject().put("tag", "action").putArray("actions");
        ObjectNode button = actions.addObject().put("tag", "button").put("type", "primary");
        button.putObject("text").put("tag", "plain_text").put("content", "查看");
        button.put("url", message.getCard().getLinkUrl());
        yield new FeishuOutboundMessage(receiveId, "interactive", card.toString());
      }
      case QUICK_REPLY -> {
        ObjectNode card = cardSkeleton();
        ArrayNode elements = card.putArray("elements");
        elements
            .addObject()
            .put("tag", "div")
            .putObject("text")
            .put("tag", "plain_text")
            .put("content", message.getQuickReply().getPromptText());
        ArrayNode actions = elements.addObject().put("tag", "action").putArray("actions");
        message
            .getQuickReply()
            .getOptionsList()
            .forEach(
                option -> {
                  ObjectNode button =
                      actions.addObject().put("tag", "button").put("type", "default");
                  button
                      .putObject("text")
                      .put("tag", "plain_text")
                      .put("content", option.getLabel());
                  button.putObject("value").put("option_id", option.getOptionId());
                });
        yield new FeishuOutboundMessage(receiveId, "interactive", card.toString());
      }
      default ->
          throw new IllegalArgumentException(
              "unsupported outbound content for this channel: " + message.getContentCase());
    };
  }

  private static UnifiedMessage.Builder inboundBuilder(
      String channelMessageId, String openId, long createdAtMs) {
    String messageId =
        (channelMessageId == null || channelMessageId.isBlank())
            ? UlidCreator.getUlid().toString()
            : channelMessageId;
    return UnifiedMessage.newBuilder()
        .setMessageId(messageId)
        .setChannelType(ChannelType.CHANNEL_TYPE_FEISHU)
        .setChannelInstance(FEISHU_CHANNEL_INSTANCE)
        .setDirection(MessageDirection.MESSAGE_DIRECTION_INBOUND)
        .setExternalUserId(openId)
        .setOccurredAt(
            Timestamp.newBuilder()
                .setSeconds(createdAtMs / 1000)
                .setNanos((int) (createdAtMs % 1000) * 1_000_000)
                .build());
  }

  private static ObjectNode cardSkeleton() {
    ObjectNode card = MAPPER.createObjectNode();
    card.putObject("config").put("wide_screen_mode", true);
    return card;
  }

  private static JsonNode readJson(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (Exception e) {
      throw new IllegalArgumentException("invalid feishu content json", e);
    }
  }
}
