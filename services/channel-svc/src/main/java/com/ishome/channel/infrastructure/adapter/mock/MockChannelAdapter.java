package com.ishome.channel.infrastructure.adapter.mock;

import com.ishome.channel.domain.port.ChannelAdapter;
import com.ishome.channel.v1.ChannelCapability;
import com.ishome.channel.v1.ChannelGrade;
import com.ishome.channel.v1.HumanTakeover;
import com.ishome.channel.v1.UnifiedMessage;
import com.ishome.common.v1.ChannelType;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 内置 mock 渠道 adapter（对齐文档 §6.4）：集成测试工具兼本地开发环境，会话级能力全开。
 *
 * <p>ChannelType 注册表约定：仅限本地开发与自动化测试，禁止生产配置启用——profile 门控在此兑现。
 */
@Component
@Profile({"local", "dev"})
public final class MockChannelAdapter implements ChannelAdapter {

  private final List<UnifiedMessage> sentMessages = new CopyOnWriteArrayList<>();
  private final AtomicLong sendCounter = new AtomicLong();

  @Override
  public ChannelType channelType() {
    return ChannelType.CHANNEL_TYPE_MOCK;
  }

  @Override
  public ChannelCapability capability() {
    return ChannelCapability.newBuilder()
        .setCanSendProactive(true)
        .setSupportsCard(true)
        .setSupportsQuickReply(true)
        .setHumanTakeover(HumanTakeover.HUMAN_TAKEOVER_NONE)
        .setChannelGrade(ChannelGrade.CHANNEL_GRADE_SESSION)
        .build();
  }

  @Override
  public String send(UnifiedMessage message) {
    sentMessages.add(message);
    return "mock-" + sendCounter.incrementAndGet();
  }

  public List<UnifiedMessage> listSentMessages() {
    return List.copyOf(sentMessages);
  }

  public void clearSentMessages() {
    sentMessages.clear();
  }
}
