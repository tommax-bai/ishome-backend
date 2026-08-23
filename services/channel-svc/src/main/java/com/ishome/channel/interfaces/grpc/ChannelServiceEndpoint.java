package com.ishome.channel.interfaces.grpc;

import com.ishome.channel.application.CapabilityAppService;
import com.ishome.channel.application.OutboundMessageAppService;
import com.ishome.channel.application.OutboundSendResult;
import com.ishome.channel.domain.UnknownChannelException;
import com.ishome.channel.v1.ChannelServiceGrpc;
import com.ishome.channel.v1.GetCapabilityRequest;
import com.ishome.channel.v1.GetCapabilityResponse;
import com.ishome.channel.v1.SendMessageRequest;
import com.ishome.channel.v1.SendMessageResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

/** ChannelService gRPC 入站端点（interfaces 层，等价 REST controller 的 gRPC 形态）。 */
@Component
public class ChannelServiceEndpoint extends ChannelServiceGrpc.ChannelServiceImplBase {

  private final OutboundMessageAppService outboundMessageAppService;
  private final CapabilityAppService capabilityAppService;

  public ChannelServiceEndpoint(
      OutboundMessageAppService outboundMessageAppService,
      CapabilityAppService capabilityAppService) {
    this.outboundMessageAppService = outboundMessageAppService;
    this.capabilityAppService = capabilityAppService;
  }

  @Override
  public void sendMessage(
      SendMessageRequest request, StreamObserver<SendMessageResponse> responseObserver) {
    try {
      OutboundSendResult result =
          outboundMessageAppService.send(request.getMessage(), request.getIdempotencyKey());
      responseObserver.onNext(
          SendMessageResponse.newBuilder()
              .setMessageId(result.messageId())
              .setChannelMessageId(result.channelMessageId())
              .build());
      responseObserver.onCompleted();
    } catch (IllegalArgumentException e) {
      responseObserver.onError(
          Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
    } catch (UnknownChannelException e) {
      responseObserver.onError(
          Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void getCapability(
      GetCapabilityRequest request, StreamObserver<GetCapabilityResponse> responseObserver) {
    try {
      responseObserver.onNext(
          GetCapabilityResponse.newBuilder()
              .setCapability(
                  capabilityAppService.getCapability(
                      request.getChannelType(), request.getChannelInstance()))
              .build());
      responseObserver.onCompleted();
    } catch (UnknownChannelException e) {
      responseObserver.onError(
          Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
    }
  }
}
