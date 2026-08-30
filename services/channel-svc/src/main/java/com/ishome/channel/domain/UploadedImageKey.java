package com.ishome.channel.domain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 用户上传件在私有对象存储里的键——**由图片内容本身确定性推得，不是分配的**。
 *
 * <p>唯一真源：ishome-contracts {@code registries/object_keys.md}（只增不改）；本类的模板串是它的**逐字副本**。
 * 读这张图的一侧（生成侧解析，Python）持另一份逐字副本——两个仓两种语言谁也不能 import 谁， 只能靠同一条键接头。
 *
 * <p>**取内容自身作键**，键里因此没有：用户标识（生成侧不知用户是谁是红线，而键是它的入参）、 渠道方言（方言只在 adapter 与 raw_payload
 * 里）、新铸的流水号（渠道事件会重推，铸一次就多一个 没人认领的对象）。同一张图重推多少次都写同一个键、同样的字节，天然幂等。
 *
 * <p>两个人发同一个文件会落同一个对象——**这是特性不是冲突**：想知道这条键得先有这份字节，猜不出来； 谁传的这件事记在入站消息记录里，不写进键。
 */
public final class UploadedImageKey {

  /** contracts {@code registries/object_keys.md} 的逐字副本。 */
  private static final String TEMPLATE = "uploads/%s/original.%s";

  private UploadedImageKey() {}

  /** 这张图的对象键。{@code original} 说的是"用户发来的那一份"，派生物另有键（用时进表）。 */
  public static String of(byte[] imageBytes, UploadedImageFormat format) {
    if (imageBytes == null || imageBytes.length == 0) {
      throw new IllegalArgumentException("空字节推不出对象键：图没取下来就别往下走");
    }
    return TEMPLATE.formatted(sha256Hex(imageBytes), format.extension());
  }

  private static String sha256Hex(byte[] imageBytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(imageBytes));
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 是 JDK 必备算法，走到这里说明运行时残缺，不是业务能处理的情况
      throw new IllegalStateException("运行时没有 SHA-256", e);
    }
  }
}
