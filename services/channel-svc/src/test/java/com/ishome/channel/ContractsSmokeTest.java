package com.ishome.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.ishome.channel.v1.TextContent;
import com.ishome.channel.v1.UnifiedMessage;
import com.ishome.common.v1.ChannelType;
import com.ishome.common.v1.CognitiveState;
import com.ishome.design.v1.DesignServiceGrpc;
import org.junit.jupiter.api.Test;

/**
 * contracts SDK（shared/contracts ← ishome-contracts@v0.1.2）classpath 冒烟：
 * 三域生成类型可引用、可构建即通过（编译期已验证大半，断言防空壳）。
 */
class ContractsSmokeTest {

  @Test
  void contractsTypesResolveAcrossAllThreeDomains() {
    UnifiedMessage message =
        UnifiedMessage.newBuilder()
            .setChannelType(ChannelType.CHANNEL_TYPE_FEISHU)
            .setText(TextContent.newBuilder().setText("hello").build())
            .build();
    assertEquals(ChannelType.CHANNEL_TYPE_FEISHU, message.getChannelType());
    assertNotNull(CognitiveState.COGNITIVE_STATE_USER_CONFIRMED);
    assertNotNull(DesignServiceGrpc.SERVICE_NAME);
  }
}
