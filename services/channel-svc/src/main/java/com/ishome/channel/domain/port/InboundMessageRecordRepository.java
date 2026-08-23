package com.ishome.channel.domain.port;

import com.ishome.channel.v1.UnifiedMessage;

/** 入站消息幂等去重仓储 port：渠道消息唯一键 = (channel_type, channel_instance, 渠道原生消息 id)。 渠道事件重推（飞书处理超时重投等）在此拦截。 */
public interface InboundMessageRecordRepository {

  /** 首见则落记录并返回 true；唯一键已存在（重推）返回 false，调用方跳过下游中继。 */
  boolean recordIfFirstSeen(UnifiedMessage message);
}
