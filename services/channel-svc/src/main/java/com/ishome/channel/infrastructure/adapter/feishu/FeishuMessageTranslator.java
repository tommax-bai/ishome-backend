package com.ishome.channel.infrastructure.adapter.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.f4b6a3.ulid.UlidCreator;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import com.ishome.channel.domain.UploadedImage;
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

  /**
   * image_url 承载飞书 image_key 的 scheme，**现只用于出站**（把一张已在飞书那边的图发回给用户）。
   *
   * <p>入站不再走它：用户发来的图由渠道侧取下来落私有桶，统一消息里带的是**桶里的对象键** （{@link
   * com.ishome.channel.domain.UploadedImageKey}）——下游拿着 image_key 什么也做不了， 而凭证只有渠道侧有。出站从我们自己的桶发图属
   * TODO(media) 的另一半，触发条件写死＝ 生成的图要送回会话时。
   */
  static final String FEISHU_IMAGE_SCHEME = "feishu-image://";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private FeishuMessageTranslator() {}

  /**
   * 入站：飞书消息事件 → 统一模型。不支持的 msg_type 返回 empty（调用方记日志跳过）。
   *
   * <p>message_id 直接采用飞书原生消息 id：事件重推（处理超时未及时 ack 时飞书会重投同一事件） 在 design-svc 幂等去重处必须命中同一
   * id，否则同一条用户消息会被回复多遍（2026-08-23 真机事故）。
   *
   * <p>本方法是纯函数、不做 IO：图**必须由调用方先取下来落桶**，把结果作为 {@code uploadedImage} 传进来 （{@link #inboundImageKey}
   * 给出要取的那把 key）。msg_type=image 而不带它即抛—— 静默丢图是这条线上代价最大的一种失败，不给它留口子。
   *
   * @param uploadedImage 图片消息填已落桶的那张图；其余类型传 {@link Optional#empty()}
   */
  static Optional<UnifiedMessage> toInboundMessage(
      String openId,
      String feishuMessageId,
      String msgType,
      String contentJson,
      long createdAtMs,
      Optional<UploadedImage> uploadedImage) {
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
                          .setObjectKey(
                              uploadedImage
                                  .orElseThrow(
                                      () ->
                                          new IllegalArgumentException(
                                              "图片消息必须先落桶再翻译：没有对象键就往下走等于把图丢了"))
                                  .objectKey())
                          .setMimeType(uploadedImage.get().mimeType())
                          .build())
                  .build());
      default -> Optional.empty();
    };
  }

  /**
   * 入站图片消息里的飞书 image_key；非图片消息返回 empty。
   *
   * <p>方言解析只在本类：调用方拿着这把 key 去取图（凭证也只在渠道侧），取回来落桶后再回头调 {@link #toInboundMessage}。拆成两步是因为取图是 IO——本类不做
   * IO，而 IO 又必须在事件 ack 之后。
   */
  static Optional<String> inboundImageKey(String msgType, String contentJson) {
    if (!"image".equals(msgType)) {
      return Optional.empty();
    }
    String imageKey = readJson(contentJson).path("image_key").asText();
    if (imageKey.isBlank()) {
      throw new IllegalArgumentException("飞书图片消息没有 image_key，取不到图：" + contentJson);
    }
    return Optional.of(imageKey);
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

  /**
   * 出站：渠道侧自己要对用户说的一句话（如"这张图没取下来"）→ 统一模型。
   *
   * <p>message_id 由入站那条消息的 id 加后缀推得，**同一次失败推得同一个 id**：飞书事件重推时 出站幂等键命中既有记录，用户不会被同一句话说两遍。
   */
  static UnifiedMessage toOutboundText(String messageId, String openId, String text) {
    return UnifiedMessage.newBuilder()
        .setMessageId(messageId)
        .setChannelType(ChannelType.CHANNEL_TYPE_FEISHU)
        .setChannelInstance(FEISHU_CHANNEL_INSTANCE)
        .setDirection(MessageDirection.MESSAGE_DIRECTION_OUTBOUND)
        .setExternalUserId(openId)
        .setText(TextContent.newBuilder().setText(text).build())
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
