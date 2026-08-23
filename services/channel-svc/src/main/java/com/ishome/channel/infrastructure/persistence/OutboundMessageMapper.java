package com.ishome.channel.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** svc_channel.outbound_messages Mapper：幂等键唯一，并发重复写时首写胜出。 */
@Mapper
public interface OutboundMessageMapper {

  @Insert(
      "INSERT INTO outbound_messages (id, idempotency_key, message_id, channel_type,"
          + " channel_instance, external_user_id, channel_message_id) VALUES (#{id},"
          + " #{idempotencyKey}, #{messageId}, #{channelType}, #{channelInstance},"
          + " #{externalUserId}, #{channelMessageId}) ON CONFLICT (idempotency_key) DO NOTHING")
  int insertIfAbsent(OutboundMessagePO record);

  @Select(
      "SELECT * FROM outbound_messages WHERE idempotency_key = #{idempotencyKey}"
          + " AND deleted_at IS NULL")
  OutboundMessagePO findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
}
