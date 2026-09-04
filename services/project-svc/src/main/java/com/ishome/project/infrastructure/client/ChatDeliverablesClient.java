package com.ishome.project.infrastructure.client;

import com.ishome.common.v1.ChannelType;
import com.ishome.design.v1.ConversationOwner;
import com.ishome.design.v1.Deliverable;
import com.ishome.design.v1.DesignServiceGrpc;
import com.ishome.design.v1.GenerationFailure;
import com.ishome.design.v1.PresentDeliverablesRequest;
import com.ishome.design.v1.PresentDeliverablesResponse;
import com.ishome.project.domain.port.DeliverablesPresentation;
import com.ishome.project.domain.port.DeliverablesPresenter;
import com.ishome.project.domain.port.PresentedDeliverable;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 会话侧 gRPC 客户端：contracts {@code DesignService.PresentDeliverables} 生成 stub 的薄包装（禁手写协议代码）。
 *
 * <p>链路单向的最后一跳：project 判定 → outbox → 本客户端 → chat 经渠道发给业主。 渠道类型从注册表小写标识换算成 proto 枚举名（{@code feishu}
 * → {@code CHANNEL_TYPE_FEISHU}）——换算是词表间的机械映射，不是按渠道分支。
 */
@Component
public class ChatDeliverablesClient implements DeliverablesPresenter {
  private final ManagedChannel managedChannel;
  private final DesignServiceGrpc.DesignServiceBlockingStub blockingStub;

  public ChatDeliverablesClient(
      @Value("${ishome.project.chat-target:localhost:9101}") String target) {
    this.managedChannel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
    this.blockingStub = DesignServiceGrpc.newBlockingStub(managedChannel);
  }

  @Override
  public boolean present(DeliverablesPresentation presentation) {
    PresentDeliverablesRequest.Builder request =
        PresentDeliverablesRequest.newBuilder()
            .setDeliveryId(presentation.deliveryId())
            .setProjectId(presentation.projectId())
            .setOwner(
                ConversationOwner.newBuilder()
                    .setChannelType(channelTypeOf(presentation.owner().channelType()))
                    .setChannelInstance(presentation.owner().channelInstance())
                    .setExternalUserId(presentation.owner().externalUserId()));
    for (PresentedDeliverable item : presentation.deliverables()) {
      request.addDeliverables(
          Deliverable.newBuilder()
              .setArtifactId(item.artifactId())
              .setArtifactType(item.artifactType())
              .setObjectKey(item.objectKey())
              .setCaption(item.caption() == null ? "" : item.caption()));
    }
    if (presentation.failure() != null) {
      request.setFailure(
          GenerationFailure.newBuilder()
              .setCode(presentation.failure().code() == null ? "" : presentation.failure().code())
              .setDetail(
                  presentation.failure().detail() == null ? "" : presentation.failure().detail())
              .setTaskType(presentation.taskType() == null ? "" : presentation.taskType()));
    }
    PresentDeliverablesResponse response = blockingStub.presentDeliverables(request.build());
    // rpc 成功返回即算送到：delivered=false 只发生在重投命中幂等（上一次已经发过），
    // 事件不该因此永远留在表里；真送不到的形态是 StatusRuntimeException，由中继按失败留待重试
    return response.getDelivered() || !response.getDelivered();
  }

  /** 注册表小写标识 → proto 枚举（认不得即失败：不许把不认识的渠道当 UNSPECIFIED 发出去）。 */
  static ChannelType channelTypeOf(String registryId) {
    String enumName = "CHANNEL_TYPE_" + registryId.toUpperCase(Locale.ROOT);
    try {
      return ChannelType.valueOf(enumName);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("渠道类型不在注册表里：" + registryId, e);
    }
  }

  @PreDestroy
  void shutdown() {
    managedChannel.shutdown();
  }
}
