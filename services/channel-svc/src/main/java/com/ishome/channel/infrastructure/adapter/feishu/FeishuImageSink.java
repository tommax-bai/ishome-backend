package com.ishome.channel.infrastructure.adapter.feishu;

/**
 * 把一张图交给飞书、换回它自家 image_key 的接缝：真实现 {@link FeishuImageUploader} 走开放平台， adapter 单测注入假件。
 *
 * <p>包内可见——**上传这件事不越出 adapter 包**：应用凭证只有渠道侧有，`image_key` 又是渠道方言 （方言只存在于 adapter 与
 * `raw_payload`）。接缝立在飞书那条网络边界上，位置与入站的 {@link FeishuImageSource} 对称：立在这里是为了能测"我们自己桶里的图换回 image_key
 * 发出去"这一整条，不是为了给别的层留调用口。
 */
interface FeishuImageSink {

  /**
   * 上传图片字节，返回飞书 image_key。
   *
   * @param imageBytes 图的字节（调用方已按对象键从我们自己的私有桶取出来）
   * @param objectKey 这张图在私有桶里的键，只用来给临时文件起名与写日志——格式由飞书按字节认，不按名字猜
   * @throws RuntimeException 传不上去——响亮失败，不返回空 key 让业主收到一条打不开的图
   */
  String upload(byte[] imageBytes, String objectKey);
}
