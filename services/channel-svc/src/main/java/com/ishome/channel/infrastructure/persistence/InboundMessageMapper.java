package com.ishome.channel.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/** svc_channel.inbound_messages Mapper：唯一键冲突（渠道事件重推）时不落行、返回 0。 */
@Mapper
public interface InboundMessageMapper {

  @Insert(
      "INSERT INTO inbound_messages (id, channel_type, channel_instance, channel_message_id,"
          + " external_user_id, occurred_at) VALUES (#{id}, #{channelType}, #{channelInstance},"
          + " #{channelMessageId}, #{externalUserId}, #{occurredAt}) ON CONFLICT (channel_type,"
          + " channel_instance, channel_message_id) DO NOTHING")
  int insertIfAbsent(InboundMessagePO record);
}
