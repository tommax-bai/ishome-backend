package com.ishome.channel.application;

import com.ishome.channel.domain.ChannelAdapterRegistry;
import com.ishome.channel.domain.port.ChannelAdapter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 装配注册表：收集容器内全部 ChannelAdapter（各 adapter 是否启用由各自的凭证条件决定）。 */
@Configuration
public class ChannelAdapterConfig {

  @Bean
  public ChannelAdapterRegistry channelAdapterRegistry(List<ChannelAdapter> adapters) {
    return new ChannelAdapterRegistry(adapters);
  }
}
