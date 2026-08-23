/**
 * 收发幂等仓储 PG 实现（MyBatis + Flyway，svc_channel schema）：入站去重靠唯一键 + ON CONFLICT DO NOTHING， 出站防重发靠
 * idempotency_key 唯一键。会话消息原文归 chat-svc（svc_chat），不在此处。
 */
package com.ishome.channel.infrastructure.persistence;
