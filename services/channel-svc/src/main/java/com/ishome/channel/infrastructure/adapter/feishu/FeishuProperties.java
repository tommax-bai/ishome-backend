package com.ishome.channel.infrastructure.adapter.feishu;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 飞书接入实例凭证（配置键白名单②：channel.{type}.{instance}.{key}，规范 §6.2）。
 *
 * <p>当前单实例 ishome-prod；多实例化时改为 Map 绑定，type 不新造（规范 §6.1）。
 */
@ConfigurationProperties(prefix = "channel.feishu.ishome-prod")
public record FeishuProperties(String appId, String appSecret) {}
