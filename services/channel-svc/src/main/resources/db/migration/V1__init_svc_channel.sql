-- svc_channel 首批表：消息收发的持久幂等（IM 消息不可撤回，重发代价高）。
-- 数据纪律（技术架构 §6.4）：主键 ULID 字符串；时间戳 UTC timestamptz；created_at/updated_at 全表必备；
-- 软删统一 deleted_at；枚举存字符串（contracts ChannelType 枚举名逐字一致）。
-- 会话消息原文存储归 chat-svc（svc_chat，Python 侧），本 schema 只记渠道收发事实。

CREATE SCHEMA IF NOT EXISTS svc_channel;

-- 入站消息幂等去重：渠道消息唯一键 (channel_type, channel_instance, channel_message_id)。
-- channel_message_id = 渠道原生消息 id（飞书事件重推必须命中同一 id——2026-08-23 真机事故的教训）。
CREATE TABLE inbound_messages (
    id                 varchar(26)  PRIMARY KEY,
    channel_type       varchar(64)  NOT NULL,
    channel_instance   varchar(64)  NOT NULL,
    channel_message_id varchar(128) NOT NULL,
    external_user_id   varchar(128),
    occurred_at        timestamptz,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    deleted_at         timestamptz,
    CONSTRAINT uk_inbound_channel_message
        UNIQUE (channel_type, channel_instance, channel_message_id)
);

-- 出站发送记录：SendMessage 幂等键防重发 + 全量审计留痕（idempotency_key 空白的发送记 NULL，不参与防重）。
CREATE TABLE outbound_messages (
    id                 varchar(26)  PRIMARY KEY,
    idempotency_key    varchar(128),
    message_id         varchar(128) NOT NULL,
    channel_type       varchar(64)  NOT NULL,
    channel_instance   varchar(64),
    external_user_id   varchar(128),
    channel_message_id varchar(128) NOT NULL,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    deleted_at         timestamptz,
    CONSTRAINT uk_outbound_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_outbound_external_user ON outbound_messages (external_user_id);
