package com.ishome.channel.domain;

/**
 * 渠道能力声明（capability descriptor，对齐文档 §6.3）。调用方只查能力、按能力降级： 无卡片→纯链接；无主动窗口→等用户下次发言或降级触达级渠道召回。
 *
 * <p>会话级渠道门槛 = text + image + link；低于门槛的渠道只能做触达级（通知召回）。
 *
 * @param canSendProactive 主动发送（含窗口与频率规则引用，规则是策略数据不写死代码）
 * @param supportsCard 卡片消息
 * @param supportsQuickReply 快捷回复按钮
 * @param humanTakeover 渠道协议自身的接管形态（渠道属性的客观描述；本系统不使用该能力——V1.3）
 * @param mediaLimitsNote 图片/文件尺寸与类型限制（落地时按开放平台现行限制核实）
 */
public record ChannelCapability(
    boolean canSendProactive,
    boolean supportsCard,
    boolean supportsQuickReply,
    HumanTakeover humanTakeover,
    String mediaLimitsNote) {}
