package com.ishome.channel.infrastructure.adapter.mock;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.f4b6a3.ulid.UlidCreator;
import com.google.protobuf.Timestamp;
import com.ishome.channel.domain.port.InboundMessageRelay;
import com.ishome.channel.v1.MessageDirection;
import com.ishome.channel.v1.TextContent;
import com.ishome.channel.v1.UnifiedMessage;
import com.ishome.common.v1.ChannelType;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * mock 渠道的"接入协议"：HTTP 注入入站消息 + 查询/清空出站捕获。
 *
 * <p>web 端点落在 adapter 包内而非 interfaces 层：它是 mock 渠道的传输形态（等价飞书的长连接）， 渠道传输属 adapter
 * 可插拔单元；渠道名字面量也因此只出现在白名单①的包内（规范 §6.2）。
 */
@RestController
@Profile({"local", "dev"})
public class MockChannelWebhook {

  static final String MOCK_CHANNEL_INSTANCE = "mock:local";

  private final InboundMessageRelay inboundMessageRelay;
  private final MockChannelAdapter mockChannelAdapter;

  public MockChannelWebhook(
      InboundMessageRelay inboundMessageRelay, MockChannelAdapter mockChannelAdapter) {
    this.inboundMessageRelay = inboundMessageRelay;
    this.mockChannelAdapter = mockChannelAdapter;
  }

  @PostMapping("/mock/channels/inbound")
  public MockInboundResponse injectInbound(@RequestBody MockInboundRequest request) {
    Instant now = Instant.now();
    UnifiedMessage message =
        UnifiedMessage.newBuilder()
            .setMessageId(UlidCreator.getUlid().toString())
            .setChannelType(ChannelType.CHANNEL_TYPE_MOCK)
            .setChannelInstance(MOCK_CHANNEL_INSTANCE)
            .setDirection(MessageDirection.MESSAGE_DIRECTION_INBOUND)
            .setExternalUserId(request.userId())
            .setUserId(request.userId())
            .setOccurredAt(
                Timestamp.newBuilder()
                    .setSeconds(now.getEpochSecond())
                    .setNanos(now.getNano())
                    .build())
            .setText(TextContent.newBuilder().setText(request.text()).build())
            .build();
    String messageId = inboundMessageRelay.relay(message);
    return new MockInboundResponse(messageId);
  }

  @GetMapping("/mock/channels/outbound")
  public List<MockOutboundResponse> listOutbound() {
    return mockChannelAdapter.listSentMessages().stream()
        .map(
            message ->
                new MockOutboundResponse(
                    message.getMessageId(),
                    message.getExternalUserId(),
                    message.getContentCase().name().toLowerCase(java.util.Locale.ROOT),
                    message.hasText() ? message.getText().getText() : ""))
        .toList();
  }

  @DeleteMapping("/mock/channels/outbound")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void clearOutbound() {
    mockChannelAdapter.clearSentMessages();
  }

  /** 注入入站消息请求（JSON snake_case 端到端，技术架构 §6.1）。 */
  public record MockInboundRequest(
      @JsonProperty("user_id") String userId, @JsonProperty("text") String text) {}

  /** 注入受理响应。 */
  public record MockInboundResponse(@JsonProperty("message_id") String messageId) {}

  /** 出站捕获查询响应。 */
  public record MockOutboundResponse(
      @JsonProperty("message_id") String messageId,
      @JsonProperty("external_user_id") String externalUserId,
      @JsonProperty("content_type") String contentType,
      @JsonProperty("text") String text) {}
}
