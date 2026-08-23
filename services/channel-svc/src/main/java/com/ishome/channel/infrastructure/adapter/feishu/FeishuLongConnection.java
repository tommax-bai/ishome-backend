package com.ishome.channel.infrastructure.adapter.feishu;

import com.ishome.channel.domain.port.InboundMessageRelay;
import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.event.cardcallback.P2CardActionTriggerHandler;
import com.lark.oapi.event.cardcallback.model.P2CardActionTrigger;
import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 飞书长连接事件接入（对齐 §6.7：SDK 主动外连，无需公网回调地址，不经统一网关）。
 *
 * <p>webhook 验签回调模式为部署期选项（届时进网关入口表）。凭证门控同 adapter。
 */
@Component
@ConditionalOnProperty(name = "channel.feishu.ishome-prod.app_id")
public class FeishuLongConnection implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(FeishuLongConnection.class);

  private final FeishuProperties properties;
  private final InboundMessageRelay inboundMessageRelay;
  private volatile com.lark.oapi.ws.Client wsClient;
  private volatile boolean running;

  public FeishuLongConnection(
      FeishuProperties properties, InboundMessageRelay inboundMessageRelay) {
    this.properties = properties;
    this.inboundMessageRelay = inboundMessageRelay;
  }

  @Override
  public void start() {
    EventDispatcher dispatcher =
        EventDispatcher.newBuilder("", "")
            .onP2MessageReceiveV1(
                new ImService.P2MessageReceiveV1Handler() {
                  @Override
                  public void handle(P2MessageReceiveV1 event) {
                    onMessageReceived(event);
                  }
                })
            .onP2CardActionTrigger(
                new P2CardActionTriggerHandler() {
                  @Override
                  public P2CardActionTriggerResponse handle(P2CardActionTrigger event) {
                    onCardActionTriggered(event);
                    return new P2CardActionTriggerResponse();
                  }
                })
            .build();
    wsClient =
        new com.lark.oapi.ws.Client.Builder(properties.appId(), properties.appSecret())
            .eventHandler(dispatcher)
            .autoReconnect(true)
            .build();
    wsClient.start();
    running = true;
    log.info("feishu long connection started");
  }

  void onMessageReceived(P2MessageReceiveV1 event) {
    EventMessage message = event.getEvent().getMessage();
    String openId = event.getEvent().getSender().getSenderId().getOpenId();
    long createdAtMs = parseCreateTimeMs(message.getCreateTime());
    FeishuMessageTranslator.toInboundMessage(
            openId,
            message.getMessageId(),
            message.getMessageType(),
            message.getContent(),
            createdAtMs)
        .ifPresentOrElse(
            inboundMessageRelay::relay,
            () ->
                log.info(
                    "unsupported feishu msg_type skipped: {} event={}",
                    message.getMessageType(),
                    Jsons.DEFAULT.toJson(message)));
  }

  void onCardActionTriggered(P2CardActionTrigger event) {
    Object optionId = event.getEvent().getAction().getValue().get("option_id");
    if (optionId == null) {
      return;
    }
    String openId = event.getEvent().getOperator().getOpenId();
    inboundMessageRelay.relay(
        FeishuMessageTranslator.toSelectedOption(
            openId, optionId.toString(), System.currentTimeMillis()));
  }

  private static long parseCreateTimeMs(String createTime) {
    try {
      return Long.parseLong(createTime);
    } catch (NumberFormatException e) {
      return System.currentTimeMillis();
    }
  }

  @Override
  public void stop() {
    // SDK ws client 无显式 stop；长连接随进程存活，autoReconnect 负责断线重连
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
