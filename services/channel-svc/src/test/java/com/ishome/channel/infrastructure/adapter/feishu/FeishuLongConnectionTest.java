package com.ishome.channel.infrastructure.adapter.feishu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ishome.channel.domain.UploadedImageFormat;
import com.ishome.channel.domain.port.InboundFailureNotice;
import com.ishome.channel.domain.port.InboundMessageRelay;
import com.ishome.channel.domain.port.UploadedImageStore;
import com.ishome.channel.v1.UnifiedMessage;
import com.lark.oapi.service.im.v1.model.EventMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 入站图片这一段的红线回归：**图取不下来不静默丢图**——不中继，且当着用户说一句人话。
 *
 * <p>假件全部手写（本仓单测不引 mock 框架）；取图的接缝是 {@link FeishuImageSource}。
 */
class FeishuLongConnectionTest {

  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00};

  private final List<UnifiedMessage> relayed = new ArrayList<>();
  private final List<String> toldUser = new ArrayList<>();

  private final InboundMessageRelay relay =
      message -> {
        relayed.add(message);
        return message.getMessageId();
      };

  private final InboundFailureNotice notice =
      (outbound, idempotencyKey) -> toldUser.add(outbound.getText().getText());

  @Test
  void inboundImageReachesDesignSideCarryingTheObjectKey() {
    FeishuLongConnection connection =
        connectionWith((messageId, imageKey) -> PNG, (bytes, format) -> "uploads/abc/original.png");

    connection.relayInbound(imageMessage(), "ou_123", 0L);

    assertEquals(1, relayed.size());
    assertEquals("uploads/abc/original.png", relayed.get(0).getImage().getObjectKey());
    assertEquals("image/png", relayed.get(0).getImage().getMimeType());
    assertTrue(toldUser.isEmpty());
  }

  @Test
  void undownloadableImageStopsHereAndTheUserIsTold() {
    FeishuLongConnection connection =
        connectionWith(
            (messageId, imageKey) -> {
              throw new IllegalStateException("feishu image download failed");
            },
            (bytes, format) -> {
              throw new AssertionError("取不到图就不该再往桶里写");
            });

    connection.relayInbound(imageMessage(), "ou_123", 0L);

    // 不中继：下游拿到一条"用户发过图"却找不到图，比没收到更糟
    assertTrue(relayed.isEmpty());
    assertEquals(1, toldUser.size());
    assertTrue(toldUser.get(0).contains("再发一次"), toldUser.get(0));
  }

  @Test
  void unrecognizedFormatStopsHereAndTheUserIsToldWhatIsAccepted() {
    FeishuLongConnection connection =
        connectionWith(
            (messageId, imageKey) -> new byte[] {0x00, 0x01, 0x02, 0x03},
            (bytes, format) -> {
              throw new AssertionError("认不出格式就不该往桶里写");
            });

    connection.relayInbound(imageMessage(), "ou_123", 0L);

    assertTrue(relayed.isEmpty());
    assertEquals(1, toldUser.size());
    assertTrue(toldUser.get(0).contains(UploadedImageFormat.supportedForHuman()), toldUser.get(0));
  }

  @Test
  void unstorableImageStopsHereAndTheUserIsTold() {
    FeishuLongConnection connection =
        connectionWith(
            (messageId, imageKey) -> PNG,
            (bytes, format) -> {
              throw new IllegalStateException("上传件写不进私有桶");
            });

    connection.relayInbound(imageMessage(), "ou_123", 0L);

    assertTrue(relayed.isEmpty());
    assertEquals(1, toldUser.size());
  }

  @Test
  void textMessageNeverTouchesTheBucket() {
    FeishuLongConnection connection =
        connectionWith(
            (messageId, imageKey) -> {
              throw new AssertionError("文字消息没有图可取");
            },
            (bytes, format) -> {
              throw new AssertionError("文字消息没有图可存");
            });

    connection.relayInbound(textMessage(), "ou_123", 1_724_400_000_123L);

    assertEquals(1, relayed.size());
    assertEquals("你好，设计我的家", relayed.get(0).getText().getText());
  }

  private FeishuLongConnection connectionWith(
      FeishuImageSource imageSource, UploadedImageStore imageStore) {
    return new FeishuLongConnection(
        new FeishuProperties("app-id", "app-secret"), relay, imageSource, imageStore, notice);
  }

  private static EventMessage imageMessage() {
    return eventMessage("image", "{\"image_key\":\"img_v2_abc\"}");
  }

  private static EventMessage textMessage() {
    return eventMessage("text", "{\"text\":\"你好，设计我的家\"}");
  }

  private static EventMessage eventMessage(String msgType, String contentJson) {
    EventMessage message = new EventMessage();
    message.setMessageId("om_789");
    message.setMessageType(msgType);
    message.setContent(contentJson);
    message.setCreateTime("1724400000123");
    return message;
  }
}
