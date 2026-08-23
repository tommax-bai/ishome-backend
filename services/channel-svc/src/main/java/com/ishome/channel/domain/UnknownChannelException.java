package com.ishome.channel.domain;

import com.ishome.common.v1.ChannelType;

/** 请求的渠道类型没有已装配的 adapter（未接入或凭证未配置未启用）。错误码域 CHANNEL_xxx。 */
public class UnknownChannelException extends RuntimeException {

  public UnknownChannelException(ChannelType channelType) {
    super("no adapter registered for channel type: " + channelType);
  }
}
