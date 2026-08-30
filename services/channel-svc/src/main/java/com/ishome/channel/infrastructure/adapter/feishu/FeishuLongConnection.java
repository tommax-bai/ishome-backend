package com.ishome.channel.infrastructure.adapter.feishu;

import com.ishome.channel.domain.UploadedImage;
import com.ishome.channel.domain.UploadedImageFormat;
import com.ishome.channel.domain.port.InboundFailureNotice;
import com.ishome.channel.domain.port.InboundMessageRelay;
import com.ishome.channel.domain.port.UploadedImageStore;
import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.event.cardcallback.P2CardActionTriggerHandler;
import com.lark.oapi.event.cardcallback.model.P2CardActionTrigger;
import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

  /** 飞书图片消息取不到图时对用户说的三句话——各说清一件事、各给一条下一步，不是笼统的"失败了"。 */
  private static final String IMAGE_NOT_FETCHED = "这张图我没取下来（渠道那边没给），麻烦过一会儿再发一次。";

  private static final String IMAGE_FORMAT_UNKNOWN =
      "这张图的格式我认不出来，能收的是 " + UploadedImageFormat.supportedForHuman() + "，换一种再发一次。";

  private static final String IMAGE_NOT_STORED = "这张图我收到了但没存下来，麻烦过一会儿再发一次。";

  /** 失败告知的出站 message_id 后缀：由入站 id 推得，重推时同一次失败只说一遍。 */
  private static final String IMAGE_FAILURE_ID_SUFFIX = ":image-not-received";

  private final FeishuProperties properties;
  private final InboundMessageRelay inboundMessageRelay;
  private final FeishuImageSource feishuImageSource;
  private final UploadedImageStore uploadedImageStore;
  private final InboundFailureNotice inboundFailureNotice;

  /**
   * 事件处理必须快速返回 ack，否则飞书按超时重推同一事件（2026-08-23 真机事故：同步等 LLM 链路跑完导致每条消息被回复多遍）。中继（含取图这一次网络往返、含下游 LLM
   * 调用）一律异步执行，虚拟线程承载。
   */
  private final ExecutorService relayExecutor = Executors.newVirtualThreadPerTaskExecutor();

  private volatile com.lark.oapi.ws.Client wsClient;
  private volatile boolean running;

  public FeishuLongConnection(
      FeishuProperties properties,
      InboundMessageRelay inboundMessageRelay,
      FeishuImageSource feishuImageSource,
      UploadedImageStore uploadedImageStore,
      InboundFailureNotice inboundFailureNotice) {
    this.properties = properties;
    this.inboundMessageRelay = inboundMessageRelay;
    this.feishuImageSource = feishuImageSource;
    this.uploadedImageStore = uploadedImageStore;
    this.inboundFailureNotice = inboundFailureNotice;
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
    // 图片消息要先把图取下来落桶，那是一次网络往返——连同翻译与中继整个挪进异步，事件先 ack。
    relayExecutor.execute(() -> relayInbound(message, openId, createdAtMs));
  }

  /**
   * 一条入站消息从渠道方言走到统一模型：图片先取下来落私有桶，再带着**对象键**中继。
   *
   * <p>取图、认格式、落桶三步中任一步失败，**当场停在这儿**并当着用户说一句人话：不带着空键往下走
   * （下游拿到一条"用户发过图"却找不到图，比没收到更糟），也不静默丢图（他会一直等一个不来的回复）。 三句话各说清一件事、各给一条下一步，不是笼统的"失败了"。
   */
  void relayInbound(EventMessage message, String openId, long createdAtMs) {
    String feishuMessageId = message.getMessageId();
    Optional<UploadedImage> uploadedImage = Optional.empty();

    Optional<String> imageKey =
        FeishuMessageTranslator.inboundImageKey(message.getMessageType(), message.getContent());
    if (imageKey.isPresent()) {
      byte[] imageBytes;
      try {
        imageBytes = feishuImageSource.download(feishuMessageId, imageKey.get());
      } catch (RuntimeException e) {
        log.error("inbound image download failed: message_id={}", feishuMessageId, e);
        tellUser(feishuMessageId, openId, IMAGE_NOT_FETCHED);
        return;
      }

      // 不猜、不按渠道给的文件名兜底：入口判据必须确定性，一次静默降级污染整条链
      Optional<UploadedImageFormat> format = UploadedImageFormat.detect(imageBytes);
      if (format.isEmpty()) {
        log.warn(
            "inbound image format unrecognized: message_id={} bytes={}",
            feishuMessageId,
            imageBytes.length);
        tellUser(feishuMessageId, openId, IMAGE_FORMAT_UNKNOWN);
        return;
      }

      try {
        String objectKey = uploadedImageStore.store(imageBytes, format.get());
        log.info(
            "inbound image stored: message_id={} object_key={} mime={}",
            feishuMessageId,
            objectKey,
            format.get().mimeType());
        uploadedImage = Optional.of(new UploadedImage(objectKey, format.get().mimeType()));
      } catch (RuntimeException e) {
        log.error("inbound image store failed: message_id={}", feishuMessageId, e);
        tellUser(feishuMessageId, openId, IMAGE_NOT_STORED);
        return;
      }
    }

    FeishuMessageTranslator.toInboundMessage(
            openId,
            feishuMessageId,
            message.getMessageType(),
            message.getContent(),
            createdAtMs,
            uploadedImage)
        .ifPresentOrElse(
            this::relay,
            () ->
                log.info(
                    "unsupported feishu msg_type skipped: {} event={}",
                    message.getMessageType(),
                    Jsons.DEFAULT.toJson(message)));
  }

  private void tellUser(String feishuMessageId, String openId, String text) {
    String noticeId = feishuMessageId + IMAGE_FAILURE_ID_SUFFIX;
    try {
      inboundFailureNotice.notifyUser(
          FeishuMessageTranslator.toOutboundText(noticeId, openId, text), noticeId);
    } catch (RuntimeException e) {
      // 连"说一声"都失败：日志是最后一道留证，不再往上抛（异步线程里抛掉就没了）
      log.error("inbound failure notice not delivered: message_id={}", feishuMessageId, e);
    }
  }

  void onCardActionTriggered(P2CardActionTrigger event) {
    Object optionId = event.getEvent().getAction().getValue().get("option_id");
    if (optionId == null) {
      return;
    }
    String openId = event.getEvent().getOperator().getOpenId();
    com.ishome.channel.v1.UnifiedMessage selected =
        FeishuMessageTranslator.toSelectedOption(
            event.getHeader().getEventId(),
            openId,
            optionId.toString(),
            System.currentTimeMillis());
    relayExecutor.execute(() -> relay(selected));
  }

  private void relay(com.ishome.channel.v1.UnifiedMessage message) {
    try {
      inboundMessageRelay.relay(message);
    } catch (RuntimeException e) {
      log.error("inbound relay failed: message_id={}", message.getMessageId(), e);
    }
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
    relayExecutor.shutdown();
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
