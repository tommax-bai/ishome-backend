package com.ishome.channel.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * 上传件的键由内容推得。守门断言对着 contracts {@code registries/object_keys.md}——读这张图的一侧（Python）
 * 持另一份逐字副本，对不上就是接不上头。
 */
class UploadedImageKeyTest {

  private static final byte[] PNG = UploadedImageFormatTest.png();

  @Test
  void derivesKeyFromContentHash() {
    // 逐字对着注册表：uploads/{content_sha256}/original.{ext}
    String key = UploadedImageKey.of(PNG, UploadedImageFormat.PNG);
    assertTrue(key.startsWith("uploads/"), key);
    assertTrue(key.endsWith("/original.png"), key);
    String sha256Hex = key.substring("uploads/".length(), key.indexOf("/original."));
    assertEquals(64, sha256Hex.length());
    assertTrue(sha256Hex.matches("[0-9a-f]{64}"), sha256Hex);
  }

  @Test
  void sameImageAlwaysLandsOnTheSameObject() {
    // 渠道事件会重推：铸流水号就多一个没人认领的对象，取内容作键则重推多少次都是同一个
    assertEquals(
        UploadedImageKey.of(PNG, UploadedImageFormat.PNG),
        UploadedImageKey.of(PNG.clone(), UploadedImageFormat.PNG));
  }

  @Test
  void differentImagesLandOnDifferentObjects() {
    assertNotEquals(
        UploadedImageKey.of(PNG, UploadedImageFormat.PNG),
        UploadedImageKey.of(UploadedImageFormatTest.jpeg(), UploadedImageFormat.JPEG));
  }

  @Test
  void carriesNoUserOrChannelIdentity() {
    // 键是生成侧的入参，而生成侧不知用户是谁；方言也只在 adapter 与 raw_payload 里
    String key = UploadedImageKey.of(PNG, UploadedImageFormat.PNG);
    assertTrue(key.matches("uploads/[0-9a-f]{64}/original\\.png"), key);
  }

  @Test
  void refusesEmptyBytes() {
    // 图没取下来就别往下走：空字节推不出键，不给静默丢图留口子
    assertThrows(
        IllegalArgumentException.class,
        () -> UploadedImageKey.of(new byte[0], UploadedImageFormat.PNG));
    assertThrows(
        IllegalArgumentException.class, () -> UploadedImageKey.of(null, UploadedImageFormat.PNG));
  }

  @Test
  void hashIsSha256OfTheBytes() {
    // 与外部工具可对：echo -n "hello" | shasum -a 256
    String key =
        UploadedImageKey.of("hello".getBytes(StandardCharsets.UTF_8), UploadedImageFormat.PNG);
    assertEquals(
        "uploads/2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824/original.png",
        key);
  }
}
