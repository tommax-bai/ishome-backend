package com.ishome.channel.infrastructure.client;

import com.ishome.channel.domain.port.DesignConversationGateway;
import com.ishome.channel.v1.UnifiedMessage;
import com.ishome.design.v1.DesignServiceGrpc;
import com.ishome.design.v1.IngestMessageRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** design-svc gRPC 客户端（contracts 生成 stub 的薄包装，禁手写协议代码——规范 §2.1 XxxClient）。 */
@Component
public class DesignClient implements DesignConversationGateway {

  private final ManagedChannel managedChannel;
  private final DesignServiceGrpc.DesignServiceBlockingStub blockingStub;

  public DesignClient(@Value("${ishome.channel.design-target:localhost:9101}") String target) {
    this.managedChannel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
    this.blockingStub = DesignServiceGrpc.newBlockingStub(managedChannel);
  }

  @Override
  public String ingest(UnifiedMessage message) {
    return blockingStub
        .ingestMessage(IngestMessageRequest.newBuilder().setMessage(message).build())
        .getMessageId();
  }

  @PreDestroy
  void shutdown() {
    managedChannel.shutdown();
  }
}
