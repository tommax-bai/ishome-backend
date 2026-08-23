package com.ishome.channel.infrastructure.adapter.mock;

import com.ishome.channel.domain.ChannelCapability;
import com.ishome.channel.domain.HumanTakeover;
import com.ishome.channel.domain.port.ChannelAdapter;
import com.ishome.channel.domain.port.OutboundMessage;
import java.util.ArrayList;
import java.util.List;

/** 内置 mock 渠道 adapter（对齐文档 §6.4）：同时充当集成测试工具和本地开发环境。 能力全开，发送即入内存队列供断言。 */
public final class MockChannelAdapter implements ChannelAdapter {

  private final List<OutboundMessage> sentMessages = new ArrayList<>();

  @Override
  public String channelType() {
    return "mock";
  }

  @Override
  public ChannelCapability capability() {
    return new ChannelCapability(true, true, true, HumanTakeover.NONE, "");
  }

  @Override
  public void send(OutboundMessage message) {
    sentMessages.add(message);
  }

  public List<OutboundMessage> listSentMessages() {
    return List.copyOf(sentMessages);
  }
}
