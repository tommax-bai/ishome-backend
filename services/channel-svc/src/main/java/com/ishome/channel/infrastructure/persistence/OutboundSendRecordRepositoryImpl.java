package com.ishome.channel.infrastructure.persistence;

import com.github.f4b6a3.ulid.UlidCreator;
import com.ishome.channel.domain.OutboundSendRecord;
import com.ishome.channel.domain.port.OutboundSendRecordRepository;
import com.ishome.channel.v1.UnifiedMessage;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** svc_channel.outbound_messages PG 实现：幂等键唯一约束防重发，空白键仅审计留痕。 */
@Repository
public class OutboundSendRecordRepositoryImpl implements OutboundSendRecordRepository {

  private final OutboundMessageMapper outboundMessageMapper;

  public OutboundSendRecordRepositoryImpl(OutboundMessageMapper outboundMessageMapper) {
    this.outboundMessageMapper = outboundMessageMapper;
  }

  @Override
  public Optional<OutboundSendRecord> findByIdempotencyKey(String idempotencyKey) {
    return Optional.ofNullable(outboundMessageMapper.findByIdempotencyKey(idempotencyKey))
        .map(po -> new OutboundSendRecord(po.getMessageId(), po.getChannelMessageId()));
  }

  @Override
  public void recordSent(UnifiedMessage message, String idempotencyKey, String channelMessageId) {
    OutboundMessagePO po = new OutboundMessagePO();
    po.setId(UlidCreator.getUlid().toString());
    po.setIdempotencyKey(
        idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey);
    po.setMessageId(message.getMessageId());
    po.setChannelType(message.getChannelType().name());
    po.setChannelInstance(message.getChannelInstance());
    po.setExternalUserId(message.getExternalUserId());
    po.setChannelMessageId(channelMessageId);
    outboundMessageMapper.insertIfAbsent(po);
  }
}
