package com.ishome.channel.domain;

import java.util.Optional;

/**
 * 用户上传图片的格式——**按字节首部判定，不按渠道给的文件名或声明猜**。
 *
 * <p>为什么不信渠道给的名字：入口判据必须确定性、不许模糊降级（获客线红线四）。文件名是用户那台设备 写出来的，`.jpg` 里装着 HEIC
 * 是常事；而这张图下游要喂给解析，猜错格式是一次静默降级， 污染的是整条链。字节首部是图片自己说的，不用猜。
 *
 * <p>闭集之外一律不认（返回 empty，由调用方响亮失败并告诉用户换一种再发）——**新增格式是往这张表里加一行**， 不是放宽判定。
 */
public enum UploadedImageFormat {
  JPEG("image/jpeg", "jpg"),
  PNG("image/png", "png"),
  WEBP("image/webp", "webp"),
  GIF("image/gif", "gif"),
  BMP("image/bmp", "bmp");

  private final String mimeType;
  private final String extension;

  UploadedImageFormat(String mimeType, String extension) {
    this.mimeType = mimeType;
    this.extension = extension;
  }

  public String mimeType() {
    return mimeType;
  }

  /** 对象键末尾那一截（contracts {@code registries/object_keys.md} 的 {@code {ext}} 闭集）。 */
  public String extension() {
    return extension;
  }

  /** 认得出就给格式，认不出给 empty（不猜、不兜底）。 */
  public static Optional<UploadedImageFormat> detect(byte[] imageBytes) {
    if (startsWith(imageBytes, 0xFF, 0xD8, 0xFF)) {
      return Optional.of(JPEG);
    }
    if (startsWith(imageBytes, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
      return Optional.of(PNG);
    }
    // WEBP：RIFF 容器，第 8-11 字节是 WEBP（前四字节之后是文件长度，不参与判定）
    if (startsWith(imageBytes, 'R', 'I', 'F', 'F')
        && imageBytes.length >= 12
        && imageBytes[8] == 'W'
        && imageBytes[9] == 'E'
        && imageBytes[10] == 'B'
        && imageBytes[11] == 'P') {
      return Optional.of(WEBP);
    }
    if (startsWith(imageBytes, 'G', 'I', 'F', '8')) {
      return Optional.of(GIF);
    }
    if (startsWith(imageBytes, 'B', 'M')) {
      return Optional.of(BMP);
    }
    return Optional.empty();
  }

  /** 认得的格式念给用户听（失败时告诉他能发什么，不是只说"不行"）。 */
  public static String supportedForHuman() {
    return "JPG、PNG、WEBP、GIF、BMP";
  }

  private static boolean startsWith(byte[] imageBytes, int... magic) {
    if (imageBytes == null || imageBytes.length < magic.length) {
      return false;
    }
    for (int i = 0; i < magic.length; i++) {
      if ((imageBytes[i] & 0xFF) != magic[i]) {
        return false;
      }
    }
    return true;
  }
}
