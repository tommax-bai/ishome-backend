package com.ishome.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ishome.channel.interfaces.grpc.ChannelGrpcServer;
import com.ishome.channel.testsupport.PostgresIntegrationTestSupport;
import com.ishome.channel.v1.ChannelGrade;
import com.ishome.channel.v1.ChannelServiceGrpc;
import com.ishome.channel.v1.GetCapabilityRequest;
import com.ishome.channel.v1.GetCapabilityResponse;
import com.ishome.channel.v1.MessageDirection;
import com.ishome.channel.v1.SendMessageRequest;
import com.ishome.channel.v1.SendMessageResponse;
import com.ishome.channel.v1.TextContent;
import com.ishome.channel.v1.UnifiedMessage;
import com.ishome.common.v1.ChannelType;
import com.ishome.design.v1.DesignServiceGrpc;
import com.ishome.design.v1.IngestMessageRequest;
import com.ishome.design.v1.IngestMessageResponse;
import com.ishome.shared.kernel.testsupport.EnabledIfLocalPostgres;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * mock 渠道 E2E（进程内假 design-svc）：注入入站 → DesignService.IngestMessage 收到； SendMessage 出站 → mock
 * 捕获可查；幂等键防重发（真相在 svc_channel_it，PG 不可达则跳过）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@EnabledIfLocalPostgres
@Import(PostgresIntegrationTestSupport.CleanMigrateConfig.class)
class MockConversationFlowIntegrationTest {

  /** 进程内假 DesignService：捕获 IngestMessage 请求，回执 message_id。 */
  static final class FakeDesignService extends DesignServiceGrpc.DesignServiceImplBase {
    final List<IngestMessageRequest> received = new CopyOnWriteArrayList<>();

    @Override
    public void ingestMessage(
        IngestMessageRequest request, StreamObserver<IngestMessageResponse> responseObserver) {
      received.add(request);
      responseObserver.onNext(
          IngestMessageResponse.newBuilder()
              .setMessageId(request.getMessage().getMessageId())
              .build());
      responseObserver.onCompleted();
    }
  }

  static final FakeDesignService fakeDesignService = new FakeDesignService();
  static final Server fakeDesignServer;

  static {
    try {
      fakeDesignServer = ServerBuilder.forPort(0).addService(fakeDesignService).build().start();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void wireFakeDesignTarget(DynamicPropertyRegistry registry) {
    PostgresIntegrationTestSupport.register(registry);
    registry.add("ishome.channel.design-target", () -> "localhost:" + fakeDesignServer.getPort());
    registry.add("ishome.channel.grpc-port", () -> 0);
  }

  @AfterAll
  static void shutdownFakeDesign() {
    fakeDesignServer.shutdownNow();
  }

  @LocalServerPort int serverPort;
  @Autowired TestRestTemplate restTemplate;
  @Autowired ChannelGrpcServer channelGrpcServer;

  @Test
  void inboundInjectionReachesDesignService() {
    ResponseEntity<Map<String, String>> response =
        restTemplate.exchange(
            org.springframework.http.RequestEntity.post("/mock/channels/inbound")
                .body(Map.of("user_id", "u-42", "text", "客厅想要奶油风")),
            new org.springframework.core.ParameterizedTypeReference<>() {});

    assertTrue(response.getStatusCode().is2xxSuccessful());
    String messageId = response.getBody().get("message_id");
    assertEquals(1, fakeDesignService.received.size());
    UnifiedMessage forwarded = fakeDesignService.received.get(0).getMessage();
    assertEquals(messageId, forwarded.getMessageId());
    assertEquals("客厅想要奶油风", forwarded.getText().getText());
    assertEquals(ChannelType.CHANNEL_TYPE_MOCK, forwarded.getChannelType());
    assertEquals(MessageDirection.MESSAGE_DIRECTION_INBOUND, forwarded.getDirection());
  }

  @Test
  void outboundSendIsCapturedAndIdempotent() {
    ManagedChannel channel =
        ManagedChannelBuilder.forAddress("localhost", channelGrpcServer.port())
            .usePlaintext()
            .build();
    try {
      ChannelServiceGrpc.ChannelServiceBlockingStub stub =
          ChannelServiceGrpc.newBlockingStub(channel);
      SendMessageRequest request =
          SendMessageRequest.newBuilder()
              .setIdempotencyKey("it-idem-1")
              .setMessage(
                  UnifiedMessage.newBuilder()
                      .setMessageId("01ITULID0000000000000000000")
                      .setChannelType(ChannelType.CHANNEL_TYPE_MOCK)
                      .setChannelInstance("mock:local")
                      .setDirection(MessageDirection.MESSAGE_DIRECTION_OUTBOUND)
                      .setExternalUserId("u-42")
                      .setText(TextContent.newBuilder().setText("你的确认清单好了")))
              .build();

      SendMessageResponse first = stub.sendMessage(request);
      SendMessageResponse retried = stub.sendMessage(request);
      assertEquals(first.getChannelMessageId(), retried.getChannelMessageId());

      GetCapabilityResponse capability =
          stub.getCapability(
              GetCapabilityRequest.newBuilder()
                  .setChannelType(ChannelType.CHANNEL_TYPE_MOCK)
                  .build());
      assertEquals(
          ChannelGrade.CHANNEL_GRADE_SESSION, capability.getCapability().getChannelGrade());

      ResponseEntity<List<Map<String, String>>> outbound =
          restTemplate.exchange(
              org.springframework.http.RequestEntity.get("/mock/channels/outbound").build(),
              new org.springframework.core.ParameterizedTypeReference<>() {});
      List<Map<String, String>> captured =
          outbound.getBody().stream()
              .filter(m -> "u-42".equals(m.get("external_user_id")))
              .toList();
      assertEquals(1, captured.size());
      assertEquals("你的确认清单好了", captured.get(0).get("text"));
    } finally {
      channel.shutdownNow();
    }
  }
}
