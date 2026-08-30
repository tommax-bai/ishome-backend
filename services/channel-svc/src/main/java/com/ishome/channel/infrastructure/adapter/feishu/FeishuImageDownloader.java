package com.ishome.channel.infrastructure.adapter.feishu;

import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.model.GetMessageResourceReq;
import com.lark.oapi.service.im.v1.model.GetMessageResourceResp;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 把飞书消息里的图取下来（对齐 §6.7；凭证门控同 adapter）。
 *
 * <p>**为什么下载只能在渠道侧做**：取这张图要飞书应用凭证，而凭证只有渠道侧有；`image_key` 又是 渠道方言，方言只存在于 adapter 与
 * `raw_payload`。让会话侧拿着 key 自己去下载，等于把凭证和方言 一起推过边界——两条纪律一次破两条。
 *
 * <p>取图是一次网络往返，**必须在事件 ack 之后做**（同步阻塞 ack 会被飞书判超时重推同一事件， 2026-08-23 真机事故就是这么来的）；调用方 {@link
 * FeishuLongConnection} 在异步中继里调本类。
 */
@Component
@ConditionalOnProperty(name = "channel.feishu.ishome-prod.app_id")
public final class FeishuImageDownloader implements FeishuImageSource {

  private final Client client;

  public FeishuImageDownloader(FeishuProperties properties) {
    this.client = Client.newBuilder(properties.appId(), properties.appSecret()).build();
  }

  /**
   * 按消息 id + image_key 取图片字节。
   *
   * @throws IllegalStateException 取不下来——响亮失败，不返回空字节让下游拿着它跑
   */
  @Override
  public byte[] download(String feishuMessageId, String imageKey) {
    GetMessageResourceReq req =
        GetMessageResourceReq.newBuilder()
            .messageId(feishuMessageId)
            .fileKey(imageKey)
            .type("image")
            .build();
    GetMessageResourceResp resp;
    try {
      resp = client.im().messageResource().get(req);
    } catch (Exception e) {
      throw new IllegalStateException(
          "feishu image download failed: message_id=" + feishuMessageId, e);
    }
    if (!resp.success()) {
      throw new IllegalStateException(
          "feishu image download failed: message_id=%s code=%s msg=%s"
              .formatted(feishuMessageId, resp.getCode(), resp.getMsg()));
    }
    return resp.getData().toByteArray();
  }
}
