package com.ishome.channel.domain.port;

import com.ishome.channel.domain.OutboundSendRecord;
import com.ishome.channel.v1.UnifiedMessage;
import java.util.Optional;

/** 出站发送记录仓储 port：SendMessage 幂等键防重发的持久真相。 */
public interface OutboundSendRecordRepository {

  Optional<OutboundSendRecord> findByIdempotencyKey(String idempotencyKey);

  /** 渠道发送成功后落记录；idempotencyKey 空白时仅审计留痕、不参与防重。 */
  void recordSent(UnifiedMessage message, String idempotencyKey, String channelMessageId);
}
