package com.ishome.channel.domain.port;

import com.ishome.channel.v1.UnifiedMessage;

/**
 * design-svc 会话入口端口（对齐文档 §2.3：IM 渠道 → channel-svc → design-svc）。
 *
 * <p>实现走 contracts 生成的 gRPC stub（禁手写客户端），落 infrastructure（{@code DesignClient}）。
 */
public interface DesignConversationGateway {

  /**
   * 将入站统一模型消息转交 design-svc（DesignService.IngestMessage）。
   *
   * @return design 侧受理的 message_id
   */
  String ingest(UnifiedMessage message);
}
