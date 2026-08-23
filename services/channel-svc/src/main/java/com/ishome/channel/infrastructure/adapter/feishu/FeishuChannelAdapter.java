package com.ishome.channel.infrastructure.adapter.feishu;

import com.ishome.channel.domain.ChannelCapability;
import com.ishome.channel.domain.HumanTakeover;
import com.ishome.channel.domain.port.ChannelAdapter;
import com.ishome.channel.domain.port.OutboundMessage;

/**
 * 飞书 adapter（首发渠道，对齐文档 §6.7）。
 *
 * <p>事件接入：开发环境用开放平台长连接模式起步（SDK 主动外连，无需公网回调地址，不经统一网关）； webhook 模式作为部署期选项。消息映射：text/image 直映射；card →
 * 飞书卡片 JSON； quick_reply → 卡片按钮，按钮回调翻译回统一模型的"用户选择"消息。 飞书富卡片（输入框/下拉）超出五类基础模型的能力只经能力声明暴露，不进基础契约。
 *
 * <p>渠道名字面量只许出现在本 adapter 包内（白名单①，规范 §6.2）。
 */
public final class FeishuChannelAdapter implements ChannelAdapter {

  @Override
  public String channelType() {
    return "feishu";
  }

  @Override
  public ChannelCapability capability() {
    // can_send_proactive=true：应用可用范围内无发送窗口限制（对齐 §6.7）；
    // media limits 落地时按开放平台现行限制核实
    return new ChannelCapability(true, true, true, HumanTakeover.GROUP, "TODO: 按开放平台现行限制核实");
  }

  @Override
  public void send(OutboundMessage message) {
    throw new UnsupportedOperationException("TODO: 飞书开放平台接入（长连接模式起步）");
  }
}
