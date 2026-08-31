package com.ishome.channel.infrastructure.adapter.feishu;

import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.model.CreateImageReq;
import com.lark.oapi.service.im.v1.model.CreateImageReqBody;
import com.lark.oapi.service.im.v1.model.CreateImageResp;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 把我们自己私有桶里的一张图交给飞书，换回它自家的 image_key（凭证门控同 adapter）。
 *
 * <p>**为什么必须先传一遍**：飞书发图只认它自己的 image_key——我们生成的图从来没在飞书那边存在过。 桶里的字节要走到业主的聊天窗口，只有"取桶 → 上传换 key → 按 key
 * 发"这一条路。上传要应用凭证， 凭证只有渠道侧有，所以这一步只能在本 adapter 包里做（同入站取图的理由，方向相反）。
 *
 * <p>**为什么要落一个临时文件**：oapi-sdk 2.8.5 的上传入参是 {@code java.io.File}，没有字节/流的重载。 临时文件由 JDK 建（POSIX 下 600
 * 权限），传完即删、失败也删——业主的私有产物不留在本机盘上。
 */
@Component
@ConditionalOnProperty(name = "channel.feishu.ishome-prod.app_id")
public final class FeishuImageUploader implements FeishuImageSink {

  private static final Logger log = LoggerFactory.getLogger(FeishuImageUploader.class);

  /** 飞书上传图片的用途枚举值：发消息用的图（另一个值 avatar 是设置头像，本条路用不上）。 */
  private static final String IMAGE_TYPE_MESSAGE = "message";

  private static final String TEMP_FILE_PREFIX = "feishu-outbound-";

  private static final String TEMP_FILE_FALLBACK_SUFFIX = ".img";

  private final Client client;

  public FeishuImageUploader(FeishuProperties properties) {
    this.client = Client.newBuilder(properties.appId(), properties.appSecret()).build();
  }

  @Override
  public String upload(byte[] imageBytes, String objectKey) {
    if (imageBytes == null || imageBytes.length == 0) {
      throw new IllegalStateException("空字节交不给飞书：这张图没从桶里取下来（object_key=" + objectKey + "）");
    }
    Path tempFile = null;
    try {
      tempFile = Files.createTempFile(TEMP_FILE_PREFIX, tempFileSuffix(objectKey));
      Files.write(tempFile, imageBytes);
      CreateImageResp resp =
          client
              .im()
              .image()
              .create(
                  CreateImageReq.newBuilder()
                      .createImageReqBody(
                          CreateImageReqBody.newBuilder()
                              .imageType(IMAGE_TYPE_MESSAGE)
                              .image(tempFile.toFile())
                              .build())
                      .build());
      if (!resp.success()) {
        throw new IllegalStateException(
            "飞书图片上传失败（object_key=%s）：code=%s msg=%s"
                .formatted(objectKey, resp.getCode(), resp.getMsg()));
      }
      String imageKey = resp.getData().getImageKey();
      if (imageKey == null || imageKey.isBlank()) {
        throw new IllegalStateException("飞书图片上传没给回 image_key（object_key=" + objectKey + "）");
      }
      log.info(
          "outbound image uploaded to feishu: object_key={} bytes={} image_key={}",
          objectKey,
          imageBytes.length,
          imageKey);
      return imageKey;
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("飞书图片上传失败（object_key=" + objectKey + "）", e);
    } finally {
      deleteQuietly(tempFile, objectKey);
    }
  }

  /** 临时文件的后缀取对象键末尾那一截。飞书按字节认格式，后缀只是给本机文件起个看得懂的名字—— 键里没有后缀就用兜底值，**不去猜这是什么图**。 */
  private static String tempFileSuffix(String objectKey) {
    String name = objectKey.substring(objectKey.lastIndexOf('/') + 1);
    int dot = name.lastIndexOf('.');
    return dot >= 0 && dot < name.length() - 1 ? name.substring(dot) : TEMP_FILE_FALLBACK_SUFFIX;
  }

  private static void deleteQuietly(Path tempFile, String objectKey) {
    if (tempFile == null) {
      return;
    }
    try {
      Files.deleteIfExists(tempFile);
    } catch (IOException e) {
      // 删不掉不影响这次发送，但要留证：业主的私有产物不该留在本机盘上
      log.warn(
          "outbound image temp file not deleted: object_key={} path={}", objectKey, tempFile, e);
    }
  }
}
