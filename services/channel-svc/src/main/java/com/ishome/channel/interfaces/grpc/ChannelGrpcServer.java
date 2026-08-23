package com.ishome.channel.interfaces.grpc;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/** gRPC 服务器生命周期（interfaces 层入站传输）。端口 0 = 随机可用端口（集成测试用）。 */
@Component
public class ChannelGrpcServer implements SmartLifecycle {

  private final Server server;
  private volatile boolean running;

  public ChannelGrpcServer(
      ChannelServiceEndpoint channelServiceEndpoint,
      @Value("${ishome.channel.grpc-port:9102}") int grpcPort,
      @Value("${ishome.channel.grpc-bind:0.0.0.0}") String grpcBind) {
    this.server =
        NettyServerBuilder.forAddress(new InetSocketAddress(grpcBind, grpcPort))
            .addService(channelServiceEndpoint)
            .build();
  }

  /** 实际监听端口（配置 0 时为随机分配值）。 */
  public int port() {
    return server.getPort();
  }

  @Override
  public void start() {
    try {
      server.start();
      running = true;
    } catch (IOException e) {
      throw new IllegalStateException("failed to start channel grpc server", e);
    }
  }

  @Override
  public void stop() {
    server.shutdown();
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
