package com.ishome.channel.infrastructure.persistence;

import com.github.f4b6a3.ulid.UlidCreator;
import com.google.protobuf.Timestamp;
import com.ishome.channel.domain.port.InboundMessageRecordRepository;
import com.ishome.channel.v1.UnifiedMessage;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Repository;

/** svc_channel.inbound_messages PG 实现：ON CONFLICT DO NOTHING，插入行数即"是否首见"。 */
@Repository
public class InboundMessageRecordRepositoryImpl implements InboundMessageRecordRepository {

  private final InboundMessageMapper inboundMessageMapper;

  public InboundMessageRecordRepositoryImpl(InboundMessageMapper inboundMessageMapper) {
    this.inboundMessageMapper = inboundMessageMapper;
  }

  @Override
  public boolean recordIfFirstSeen(UnifiedMessage message) {
    InboundMessagePO po = new InboundMessagePO();
    po.setId(UlidCreator.getUlid().toString());
    po.setChannelType(message.getChannelType().name());
    po.setChannelInstance(message.getChannelInstance());
    po.setChannelMessageId(message.getMessageId());
    po.setExternalUserId(message.getExternalUserId());
    po.setOccurredAt(message.hasOccurredAt() ? toUtc(message.getOccurredAt()) : null);
    return inboundMessageMapper.insertIfAbsent(po) > 0;
  }

  private static OffsetDateTime toUtc(Timestamp timestamp) {
    return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos())
        .atOffset(ZoneOffset.UTC);
  }
}
