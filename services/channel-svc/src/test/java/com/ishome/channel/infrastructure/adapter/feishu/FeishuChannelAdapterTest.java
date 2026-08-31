package com.ishome.channel.infrastructure.adapter.feishu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishome.channel.domain.UploadedImageFormat;
import com.ishome.channel.domain.port.UploadedImageStore;
import com.ishome.channel.v1.ImageContent;
import com.ishome.channel.v1.MessageDirection;
import com.ishome.channel.v1.TextContent;
import com.ishome.channel.v1.UnifiedMessage;
import com.ishome.common.v1.ChannelType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 出站发图这一段：**我们自己私有桶里的一张图，怎么走到业主的飞书聊天窗口**。
 *
 * <p>断言落在 {@link FeishuChannelAdapter#toFeishuMessage}——它产出的就是 {@code send} 放到网络上的那份飞书消息体， 只差最后一次
 * HTTP。假件全部手写（本仓单测不引 mock 框架），桶与飞书上传各有一条接缝，不打真网络。
 */
class FeishuChannelAdapterTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00};

  private static final String OBJECT_KEY = "uploads/abc123/original.png";

  private final List<String> bucketReads = new ArrayList<>();
  private final List<String> uploads = new ArrayList<>();

  @Test
  void ourOwnImageIsFetchedFromTheBucketUploadedAndSentByTheKeyFeishuGaveBack() throws Exception {
    FeishuChannelAdapter adapter =
        adapterWith(
            bucketHolding(PNG),
            (imageBytes, objectKey) -> {
              uploads.add(objectKey + "/" + imageBytes.length);
              return "img_v2_uploaded";
            });

    FeishuOutboundMessage outbound =
        adapter.toFeishuMessage(
            outboundBuilder()
                .setImage(
                    ImageContent.newBuilder()
                        .setObjectKey(OBJECT_KEY)
                        .setMimeType(UploadedImageFormat.PNG.mimeType()))
                .build());

    // 按键取桶取了这一次、且取的就是消息里那把键
    assertEquals(List.of(OBJECT_KEY), bucketReads);
    // 桶里取出来的字节原样交给飞书
    assertEquals(List.of(OBJECT_KEY + "/" + PNG.length), uploads);
    // 发出去的是飞书换回来的 image_key，不是我们的对象键
    assertEquals("image", outbound.msgType());
    assertEquals(
        "img_v2_uploaded", MAPPER.readTree(outbound.contentJson()).path("image_key").asText());
    assertEquals("ou_123", outbound.receiveId());
  }

  @Test
  void feishuOwnImageIsForwardedByItsKeyWithoutTouchingBucketOrUpload() throws Exception {
    FeishuChannelAdapter adapter =
        adapterWith(bucketRefusing("飞书自家的图不该来读我们的桶"), uploadRefusing("飞书自家的图不该再传一遍"));

    FeishuOutboundMessage outbound =
        adapter.toFeishuMessage(
            outboundBuilder()
                .setImage(ImageContent.newBuilder().setImageUrl("feishu-image://img_v2_abc"))
                .build());

    assertEquals("image", outbound.msgType());
    assertEquals("img_v2_abc", MAPPER.readTree(outbound.contentJson()).path("image_key").asText());
    assertTrue(bucketReads.isEmpty());
    assertTrue(uploads.isEmpty());
  }

  @Test
  void imageWithNeitherObjectKeyNorFeishuSchemeFailsLoudly() {
    FeishuChannelAdapter adapter =
        adapterWith(bucketRefusing("没有对象键就没有桶可取"), uploadRefusing("没有图可传"));
    UnifiedMessage blindImage =
        outboundBuilder()
            .setImage(ImageContent.newBuilder().setImageUrl("https://example.com/a.png"))
            .build();

    // 静默发出一条空图 = 业主看得见、我们看不见的失败；宁可这次发送失败被上游看见
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> adapter.toFeishuMessage(blindImage));
    assertTrue(thrown.getMessage().contains("object_key"), thrown.getMessage());
    assertTrue(uploads.isEmpty());
  }

  @Test
  void textMessageNeverTouchesTheBucketOrTheUpload() throws Exception {
    FeishuChannelAdapter adapter =
        adapterWith(bucketRefusing("文字消息没有图可取"), uploadRefusing("文字消息没有图可传"));

    FeishuOutboundMessage outbound =
        adapter.toFeishuMessage(
            outboundBuilder().setText(TextContent.newBuilder().setText("三张方案图好了")).build());

    assertEquals("text", outbound.msgType());
    assertEquals("三张方案图好了", MAPPER.readTree(outbound.contentJson()).path("text").asText());
    assertTrue(bucketReads.isEmpty());
  }

  @Test
  void unreadableBucketObjectStopsHereInsteadOfSendingABrokenImage() {
    FeishuChannelAdapter adapter =
        adapterWith(bucketRefusing("取不到私有桶里的这张图"), uploadRefusing("取不到就不该往飞书传"));
    UnifiedMessage ourImage =
        outboundBuilder().setImage(ImageContent.newBuilder().setObjectKey(OBJECT_KEY)).build();

    assertThrows(IllegalStateException.class, () -> adapter.toFeishuMessage(ourImage));
  }

  private FeishuChannelAdapter adapterWith(UploadedImageStore store, FeishuImageSink sink) {
    return new FeishuChannelAdapter(new FeishuProperties("app-id", "app-secret"), store, sink);
  }

  /** 假桶：记下被取的键，回给定的字节。 */
  private UploadedImageStore bucketHolding(byte[] imageBytes) {
    return new UploadedImageStore() {
      @Override
      public String store(byte[] bytes, UploadedImageFormat format) {
        throw new AssertionError("出站这条路不写桶");
      }

      @Override
      public byte[] getImageBytes(String objectKey) {
        bucketReads.add(objectKey);
        return imageBytes;
      }
    };
  }

  private UploadedImageStore bucketRefusing(String why) {
    return new UploadedImageStore() {
      @Override
      public String store(byte[] bytes, UploadedImageFormat format) {
        throw new AssertionError("出站这条路不写桶");
      }

      @Override
      public byte[] getImageBytes(String objectKey) {
        bucketReads.add(objectKey);
        throw new IllegalStateException(why);
      }
    };
  }

  private static FeishuImageSink uploadRefusing(String why) {
    return (imageBytes, objectKey) -> {
      throw new AssertionError(why);
    };
  }

  private static UnifiedMessage.Builder outboundBuilder() {
    return UnifiedMessage.newBuilder()
        .setMessageId("01TESTULID")
        .setChannelType(ChannelType.CHANNEL_TYPE_FEISHU)
        .setDirection(MessageDirection.MESSAGE_DIRECTION_OUTBOUND)
        .setExternalUserId("ou_123");
  }
}
