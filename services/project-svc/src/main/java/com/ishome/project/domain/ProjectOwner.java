package com.ishome.project.domain;

/**
 * 项目属主——会话定位三元组（渠道类型 × 接入实例 × 渠道侧用户），与 contracts {@code ishome.design.v1.ConversationOwner} 同义。
 *
 * <p>今天没有 identity 归一，业主只在渠道里有身份；项目要能"找回"（同一个人再发一张图仍是他的项目）、产物要能 "送回"（project-svc 主动找 chat
 * 时手上没有入站消息可挂），都靠这三样。identity 归一后改为渠道无关 user_id 键控（TODO(identity)，对齐 §6.5），三列保留作渠道绑定档案。
 *
 * <p>{@code channelType} 是注册表里的小写标识（contracts registries/channel_types.md：feishu / mock …）——
 * 渠道名只许出现在数据里，不许出现在本服务的分支里。
 */
public record ProjectOwner(String channelType, String channelInstance, String externalUserId) {
  public ProjectOwner {
    if (isBlank(channelType) || isBlank(channelInstance) || isBlank(externalUserId)) {
      throw new IllegalArgumentException("项目属主三元组缺一不可：渠道类型 / 接入实例 / 渠道侧用户");
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
