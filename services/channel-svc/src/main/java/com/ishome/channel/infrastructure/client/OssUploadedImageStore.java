package com.ishome.channel.infrastructure.client;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.ishome.channel.domain.UploadedImageFormat;
import com.ishome.channel.domain.UploadedImageKey;
import com.ishome.channel.domain.port.UploadedImageStore;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 私有桶适配：把用户发来的图写进桶、把要发给业主的图按键取回来 （用户裁决 2026-08-30 晚：私有产物一律进私有桶）。
 *
 * <p>**写之前先看在不在**：键由内容推得，同一张图重发落同一个对象——已经在的就不重写。省的不是流量，
 * 是"同一份字节被覆盖写一遍"这件事本身：桶里对象的写入时间会跟着变，而它本该是这份内容第一次进来的时间。
 *
 * <p>**Content-Type 按图自己说的格式写**：写错了业主/下游拿到的就是一坨不认识的字节。 格式判定在 {@link
 * UploadedImageFormat}，此处只负责如实写下去。
 *
 * <p>凭证从配置来，代码里不留任何默认桶名或端点；缺配置时本服务照常起（收发图这两条路不通而已， 文字会话不受影响），真要收图/发图时响亮失败。
 */
@Component
public class OssUploadedImageStore implements UploadedImageStore {

  private static final Logger log = LoggerFactory.getLogger(OssUploadedImageStore.class);

  private final OSS client;
  private final String bucket;

  public OssUploadedImageStore(
      @Value("${ishome.oss.endpoint:}") String endpoint,
      @Value("${ishome.oss.bucket-private:}") String bucket,
      @Value("${ishome.oss.access-key-id:}") String accessKeyId,
      @Value("${ishome.oss.access-key-secret:}") String accessKeySecret) {
    this.bucket = bucket;
    this.client =
        endpoint.isBlank() || bucket.isBlank() || accessKeyId.isBlank() || accessKeySecret.isBlank()
            ? null
            : new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
  }

  @Override
  public String store(byte[] imageBytes, UploadedImageFormat format) {
    requireConfigured();
    String key = UploadedImageKey.of(imageBytes, format);
    try {
      if (client.doesObjectExist(bucket, key)) {
        log.info("uploaded image already stored: key={} bytes={}", key, imageBytes.length);
        return key;
      }
      ObjectMetadata metadata = new ObjectMetadata();
      metadata.setContentType(format.mimeType());
      metadata.setContentLength(imageBytes.length);
      client.putObject(bucket, key, new ByteArrayInputStream(imageBytes), metadata);
      log.info("uploaded image stored: key={} bytes={}", key, imageBytes.length);
      return key;
    } catch (OSSException | com.aliyun.oss.ClientException e) {
      throw new IllegalStateException(
          "上传件写不进私有桶（桶 %s，键 %s）：%s".formatted(bucket, key, e.getMessage()), e);
    }
  }

  /**
   * 按对象键把图取回来。
   *
   * <p>**键不在这儿解释**：传什么键取什么对象（形态真源在 contracts {@code registries/object_keys.md}）。
   * 桶里没有这个对象、凭证没配、网络断——三种都当场抛：拿着空字节往下走，业主收到的就是一条打不开的图。
   */
  @Override
  public byte[] getImageBytes(String objectKey) {
    if (objectKey == null || objectKey.isBlank()) {
      throw new IllegalArgumentException("空对象键取不到图：要发的那张图没有键就别往下走");
    }
    requireConfigured();
    try (OSSObject object = client.getObject(bucket, objectKey)) {
      byte[] imageBytes = object.getObjectContent().readAllBytes();
      log.info("private object read: key={} bytes={}", objectKey, imageBytes.length);
      return imageBytes;
    } catch (OSSException | com.aliyun.oss.ClientException | IOException e) {
      throw new IllegalStateException(
          "取不到私有桶里的这张图（桶 %s，键 %s）：%s".formatted(bucket, objectKey, e.getMessage()), e);
    }
  }

  /** 缺配置时本服务照常起，真要读写桶时才抛——凭证不入库，位置在报错里说清楚。 */
  private void requireConfigured() {
    if (client == null) {
      throw new IllegalStateException(
          "私有对象存储没配全（ishome.oss.*）——凭证放 ~/.ishome/oss-local.env（本机）"
              + "或 /opt/ishome/env/oss.env（服务器），不入库");
    }
  }

  @PreDestroy
  void shutdown() {
    if (client != null) {
      client.shutdown();
    }
  }
}
