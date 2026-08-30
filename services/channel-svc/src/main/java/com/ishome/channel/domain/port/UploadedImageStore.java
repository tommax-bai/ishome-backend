package com.ishome.channel.domain.port;

import com.ishome.channel.domain.UploadedImageFormat;

/**
 * 用户上传件的落点端口：把图写进私有对象存储，返回它的对象键。
 *
 * <p>**为什么图必须落到我们自己的桶里**：渠道给的是它自家的 image_key，只有渠道侧握着凭证能取—— 下游（会话侧、生成侧）拿着它什么也做不了。图不落桶，这条线在渠道边界上就断了。
 *
 * <p>桶是私有的：用户上传的图、解析产物、为他生成的图与册一律私有产物，对外一律签名链接 （公开与私有分桶不分前缀，获客线红线一）。
 */
public interface UploadedImageStore {

  /**
   * 存一张用户上传的图。
   *
   * @return 私有桶里的对象键（由内容推得，同一张图重复上传落同一个对象）
   * @throws IllegalStateException 存不进去——响亮失败，不返回空键让下游拿着它跑
   */
  String store(byte[] imageBytes, UploadedImageFormat format);
}
