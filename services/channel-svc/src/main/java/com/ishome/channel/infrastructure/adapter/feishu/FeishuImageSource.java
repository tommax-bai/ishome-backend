package com.ishome.channel.infrastructure.adapter.feishu;

/**
 * 取一张飞书图片的接缝：真实现 {@link FeishuImageDownloader} 走开放平台，长连接的单测注入假件。
 *
 * <p>包内可见——**取图这件事不越出 adapter 包**：凭证只有渠道侧有，`image_key` 又是渠道方言 （方言只存在于 adapter 与
 * `raw_payload`）。接缝立在这里是为了能测"图取不下来时不静默丢图"， 不是为了给别的层留调用口。
 */
interface FeishuImageSource {

  /**
   * 按消息 id + image_key 取图片字节。
   *
   * @throws RuntimeException 取不下来——响亮失败，不返回空字节让下游拿着它跑
   */
  byte[] download(String feishuMessageId, String imageKey);
}
