package com.ishome.channel.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 格式按字节首部判定：认得的给格式，认不出就是认不出——不猜、不兜底。 */
class UploadedImageFormatTest {

  @Test
  void detectsJpegPngWebpGifBmpByMagicBytes() {
    assertEquals(Optional.of(UploadedImageFormat.JPEG), UploadedImageFormat.detect(jpeg()));
    assertEquals(Optional.of(UploadedImageFormat.PNG), UploadedImageFormat.detect(png()));
    assertEquals(Optional.of(UploadedImageFormat.WEBP), UploadedImageFormat.detect(webp()));
    assertEquals(
        Optional.of(UploadedImageFormat.GIF),
        UploadedImageFormat.detect("GIF89a".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    assertEquals(
        Optional.of(UploadedImageFormat.BMP),
        UploadedImageFormat.detect("BM____".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
  }

  @Test
  void refusesToGuessUnknownBytes() {
    // 下游是几何母版与付费报告的输入，一次静默降级污染整条链——认不出就是认不出
    assertTrue(UploadedImageFormat.detect(new byte[] {0x00, 0x01, 0x02, 0x03}).isEmpty());
    assertTrue(UploadedImageFormat.detect(new byte[0]).isEmpty());
    assertTrue(UploadedImageFormat.detect(null).isEmpty());
  }

  @Test
  void refusesTruncatedMagicBytes() {
    // 只有半截 PNG 头：不足以判定就不判定
    assertTrue(UploadedImageFormat.detect(new byte[] {(byte) 0x89, 'P'}).isEmpty());
    // RIFF 容器但不是 WEBP（如 WAV），不认
    assertTrue(
        UploadedImageFormat.detect(
                "RIFF____WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
            .isEmpty());
  }

  @Test
  void extensionsMatchTheKeyRegistryClosedSet() {
    // contracts registries/object_keys.md 的 {ext} 闭集，两侧对不上就是接不上头
    assertEquals("jpg", UploadedImageFormat.JPEG.extension());
    assertEquals("png", UploadedImageFormat.PNG.extension());
    assertEquals("webp", UploadedImageFormat.WEBP.extension());
    assertEquals("gif", UploadedImageFormat.GIF.extension());
    assertEquals("bmp", UploadedImageFormat.BMP.extension());
  }

  @Test
  void tellsHumanWhatIsAccepted() {
    // 失败时要告诉用户能发什么，不是只说"不行"
    String accepted = UploadedImageFormat.supportedForHuman();
    for (UploadedImageFormat format : UploadedImageFormat.values()) {
      assertTrue(
          accepted.toLowerCase(java.util.Locale.ROOT).contains(format.extension()),
          "认得的格式没念给用户听：" + format);
    }
  }

  static byte[] jpeg() {
    return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00};
  }

  static byte[] png() {
    return new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
  }

  static byte[] webp() {
    return new byte[] {'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'};
  }
}
