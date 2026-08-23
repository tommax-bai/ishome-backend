package com.ishome.channel.application;

import com.ishome.channel.domain.ChannelAdapterRegistry;
import com.ishome.channel.v1.ChannelCapability;
import com.ishome.common.v1.ChannelType;
import org.springframework.stereotype.Service;

/** 能力查询用例：design-svc 只查能力、按能力降级（R5），本用例是查询入口。 */
@Service
public class CapabilityAppService {

  private final ChannelAdapterRegistry channelAdapterRegistry;

  public CapabilityAppService(ChannelAdapterRegistry channelAdapterRegistry) {
    this.channelAdapterRegistry = channelAdapterRegistry;
  }

  /** channelInstance 当前单实例未用，签名保留两级标识（规范 §6.1 type × instance）。 */
  public ChannelCapability getCapability(ChannelType channelType, String channelInstance) {
    return channelAdapterRegistry.getAdapter(channelType).capability();
  }
}
