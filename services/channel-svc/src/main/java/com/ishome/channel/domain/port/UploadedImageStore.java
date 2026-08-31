package com.ishome.channel.domain.port;

import com.ishome.channel.domain.UploadedImageFormat;

/**
 * 私有对象存储端口：把图写进桶拿到对象键（入站），按对象键把图取回来（出站）。
 *
 * <p>**为什么图必须落到我们自己的桶里**：渠道给的是它自家的 image_key，只有渠道侧握着凭证能取—— 下游（会话侧、生成侧）拿着它什么也做不了。图不落桶，这条线在渠道边界上就断了。
 *
 * <p>**为什么还要能读回来**：反过来同理——为业主生成的那张图只在我们自己的桶里，渠道那边从来没有过它。 要把它发出去，渠道侧得先按对象键取到字节，再按各渠道自己的规矩交上去（飞书是先换
 * image_key）。 读这一侧不问这张图是谁写进去的：用户发来的原件与生成侧的产物同在一只私有桶，都只按键取。
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

  /**
   * 按对象键取一张图的字节。
   *
   * <p>键的形态是跨仓协议，真源在 ishome-contracts {@code registries/object_keys.md}；本方法**不解释键**、
   * 不校验它长什么样——按传进来的键取，取不到就响亮失败。谁写进去的、键该长什么样，是写的那一侧的事。
   *
   * @param objectKey 私有桶里的对象键
   * @throws IllegalStateException 取不到（桶里没有、凭证没配、网络断）——响亮失败，不返回空字节让下游拿着它跑
   */
  byte[] getImageBytes(String objectKey);
}
