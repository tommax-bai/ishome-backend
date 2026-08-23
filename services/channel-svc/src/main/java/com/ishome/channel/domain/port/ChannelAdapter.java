package com.ishome.channel.domain.port;

import com.ishome.channel.v1.ChannelCapability;
import com.ishome.channel.v1.UnifiedMessage;
import com.ishome.common.v1.ChannelType;

/**
 * 渠道适配器端口（变化轴 1：IM/触达渠道，规范 §5.2）。一个渠道一个独立 adapter 包，包=可插拔单元。
 *
 * <p>统一消息模型与能力声明以 contracts 仓 channel.v1 为唯一真源（R3 稳定核）；渠道方言（飞书卡片 JSON、 image_key 等）只存在于 adapter
 * 的翻译代码和 raw_payload 里，核心代码只说统一模型的语言。
 *
 * <p>新渠道接入的验收标准（对齐文档 §6.4，R7）：只写 adapter + 能力声明 + 凭证配置， design-svc 与 contracts 零改动；做不到即本接口设计失败。
 *
 * <p>SPI 从两个真实渠道（飞书 + mock/H5 网页会话）中提取，禁止为想象中的渠道加方法。
 */
public interface ChannelAdapter {

  /** 渠道类型，取值以 contracts 仓 ChannelType 注册表为唯一真源（只增不改）。 */
  ChannelType channelType();

  /** 能力声明——渠道间真正的差异是能力不是格式；调用方按能力降级，禁止按渠道身份分支（R5）。 */
  ChannelCapability capability();

  /**
   * 发送出站消息（direction 必须为 OUTBOUND；统一模型 → 渠道方言的翻译在 adapter 内完成）。
   *
   * @return 渠道侧消息 id（方言值，仅存档用，对应 SendMessageResponse.channel_message_id）
   */
  String send(UnifiedMessage message);
}
