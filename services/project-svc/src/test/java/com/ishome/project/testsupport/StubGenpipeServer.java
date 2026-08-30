package com.ishome.project.testsupport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 编排侧入站面的替身：一个真的 HTTP server（JDK 自带），不是 mock。
 *
 * <p>用真 server 而不是打桩 RestClient，是为了让**报文真的被序列化一次**——这一跳最要紧的断言是线上字段名 （外层 snake_case、包体
 * camelCase、{@code evaluatedOn} 是 ISO 日期串），mock 掉传输层就把要验的东西一起 mock 掉了。 顺带真实地制造连接失败与 5xx，重试路径才算被走过。
 */
public final class StubGenpipeServer implements AutoCloseable {

  /** 一次预置应答：状态码 + 响应体（空体传 null）。 */
  public record Reply(int status, String body) {}

  private final HttpServer server;
  private final Deque<Reply> replies = new ArrayDeque<>();
  private final List<String> requestBodies = new ArrayList<>();

  public StubGenpipeServer() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.createContext("/api/v1/genpipe/reports", this::handle);
    server.start();
  }

  public StubGenpipeServer replying(Reply... scripted) {
    replies.addAll(List.of(scripted));
    return this;
  }

  public String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  public List<String> requestBodies() {
    return List.copyOf(requestBodies);
  }

  private void handle(HttpExchange exchange) throws IOException {
    requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    Reply reply = replies.isEmpty() ? new Reply(202, "{}") : replies.poll();
    byte[] body =
        reply.body() == null ? new byte[0] : reply.body().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(reply.status(), body.length == 0 ? -1 : body.length);
    if (body.length > 0) {
      exchange.getResponseBody().write(body);
    }
    exchange.close();
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
