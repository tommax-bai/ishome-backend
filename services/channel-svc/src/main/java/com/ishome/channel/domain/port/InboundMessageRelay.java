package com.ishome.channel.domain.port;

import com.ishome.channel.v1.UnifiedMessage;

/**
 * 入站消息中继端口：adapter 收到渠道消息、翻译成统一模型后经此进入用例层。
 *
 * <p>adapter（infrastructure）只依赖本端口，不触达 application 类——分层依赖方向由 ArchUnit 锁定， 实现由用例层提供（{@code
 * InboundMessageAppService}），Spring 装配。
 */
public interface InboundMessageRelay {

  /**
   * 中继一条入站消息（direction 必须为 INBOUND）。
   *
   * @return design 侧受理的 message_id
   */
  String relay(UnifiedMessage message);
}
