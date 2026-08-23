package com.ishome.channel.infrastructure.adapter.feishu;

import com.ishome.channel.domain.port.ChannelAdapter;
import com.ishome.channel.v1.ChannelCapability;
import com.ishome.channel.v1.ChannelGrade;
import com.ishome.channel.v1.HumanTakeover;
import com.ishome.channel.v1.UnifiedMessage;
import com.ishome.common.v1.ChannelType;
import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 飞书 adapter（首发渠道，对齐文档 §6.7）。凭证门控：`channel.feishu.ishome-prod.app_id` 配置存在才装配，缺省时本渠道整体不启用（注册表查无 →
 * CHANNEL_xxx 错误，不报启动错）。
 *
 * <p>渠道名字面量只许出现在本 adapter 包内（白名单①，规范 §6.2）。
 */
@Component
@ConditionalOnProperty(name = "channel.feishu.ishome-prod.app_id")
public final class FeishuChannelAdapter implements ChannelAdapter {

  private static final Logger log = LoggerFactory.getLogger(FeishuChannelAdapter.class);

  private final Client client;

  public FeishuChannelAdapter(FeishuProperties properties) {
    this.client = Client.newBuilder(properties.appId(), properties.appSecret()).build();
  }

  @Override
  public ChannelType channelType() {
    return ChannelType.CHANNEL_TYPE_FEISHU;
  }

  @Override
  public ChannelCapability capability() {
    // can_send_proactive=true：应用可用范围内无发送窗口限制（对齐 §6.7）。
    // human_takeover=GROUP 是渠道协议属性的客观描述，产品不使用此能力（V1.3）。
    // media_limits 落地真机联调时按开放平台现行限制核实（TODO(media)）。
    return ChannelCapability.newBuilder()
        .setCanSendProactive(true)
        .setProactivePolicyRef("feishu-default")
        .setSupportsCard(true)
        .setSupportsQuickReply(true)
        .setHumanTakeover(HumanTakeover.HUMAN_TAKEOVER_GROUP)
        .setChannelGrade(ChannelGrade.CHANNEL_GRADE_SESSION)
        .build();
  }

  @Override
  public String send(UnifiedMessage message) {
    FeishuOutboundMessage outbound = FeishuMessageTranslator.toOutboundMessage(message);
    CreateMessageReq req =
        CreateMessageReq.newBuilder()
            .receiveIdType("open_id")
            .createMessageReqBody(
                CreateMessageReqBody.newBuilder()
                    .receiveId(outbound.receiveId())
                    .msgType(outbound.msgType())
                    .content(outbound.contentJson())
                    // 渠道侧幂等（uuid 相同则飞书不重发），复用统一模型 message_id
                    .uuid(message.getMessageId())
                    .build())
            .build();
    try {
      CreateMessageResp resp = client.im().message().create(req);
      if (!resp.success()) {
        throw new IllegalStateException(
            "feishu send failed: code=" + resp.getCode() + " msg=" + resp.getMsg());
      }
      log.info(
          "feishu message sent: message_id={} feishu_message_id={} msg_type={}",
          message.getMessageId(),
          resp.getData().getMessageId(),
          outbound.msgType());
      return resp.getData().getMessageId();
    } catch (Exception e) {
      throw new IllegalStateException("feishu send failed", e);
    }
  }
}
