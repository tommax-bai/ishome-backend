package com.ishome.channel.domain;

import com.ishome.channel.domain.port.ChannelAdapter;
import com.ishome.common.v1.ChannelType;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 渠道 adapter 注册表（变化轴 1 的运行时形态）：按 ChannelType 路由到 adapter。
 *
 * <p>核心代码经此按类型取 adapter，再按能力分支（R5）；禁止在任何调用点写渠道身份分支。
 */
public final class ChannelAdapterRegistry {

  private final Map<ChannelType, ChannelAdapter> adaptersByType;

  public ChannelAdapterRegistry(List<ChannelAdapter> adapters) {
    this.adaptersByType =
        adapters.stream()
            .collect(Collectors.toUnmodifiableMap(ChannelAdapter::channelType, adapter -> adapter));
  }

  /** get 语义：必得，取不到抛异常（规范 §三 查询命名规则）。 */
  public ChannelAdapter getAdapter(ChannelType channelType) {
    ChannelAdapter adapter = adaptersByType.get(channelType);
    if (adapter == null) {
      throw new UnknownChannelException(channelType);
    }
    return adapter;
  }
}
