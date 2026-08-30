package com.ishome.channel.domain.port;

import com.ishome.channel.v1.UnifiedMessage;

/**
 * 入站没收下时，渠道侧当着用户说一句话的端口。
 *
 * <p>**为什么要有这条路**：用户发了图，系统这边取不下来——不说，他就一直等一个永远不来的回复。 失败要响亮，而"响亮"对用户成立的形式只有一种：他看得见（获客线红线：失败要说人话）。
 *
 * <p>说的是**渠道侧自己的失败**（图没取下来、格式不认识），不是业务结论——这条路不经会话侧、 不产生业务事实，会话侧也因此不会以为用户发过一张能用的图。
 *
 * <p>adapter（infrastructure）只依赖本端口，实现由用例层提供（{@code OutboundMessageAppService}）—— 分层依赖方向由 ArchUnit
 * 锁定，同 {@link InboundMessageRelay}。
 */
public interface InboundFailureNotice {

  /**
   * 把一句话发回给用户。
   *
   * @param outbound 出站统一模型消息（direction 必须为 OUTBOUND）
   * @param idempotencyKey 幂等键——渠道事件会重推，同一次失败只说一遍
   */
  void notifyUser(UnifiedMessage outbound, String idempotencyKey);
}
