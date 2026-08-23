package com.ishome.shared.kernel.testsupport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 本地开发 PG 坐标（docker compose 项目 ishome-dev，容器 ishome-dev-postgres）。
 *
 * <p>缺省 localhost:15432/ishome，与各服务 application.yml 的数据源默认值一致；部署/CI 经 {@code ISHOME_DB_*}
 * 环境变量覆盖。仅供集成测试装配数据源属性与可达性探测。
 */
public final class LocalPostgres {

  private static final int CONNECT_TIMEOUT_MS = 500;

  private LocalPostgres() {}

  public static String host() {
    return envOr("ISHOME_DB_HOST", "localhost");
  }

  public static int port() {
    return Integer.parseInt(envOr("ISHOME_DB_PORT", "15432"));
  }

  public static String database() {
    return envOr("ISHOME_DB_NAME", "ishome");
  }

  public static String username() {
    return envOr("ISHOME_DB_USER", "ishome");
  }

  public static String password() {
    return envOr("ISHOME_DB_PASSWORD", "ishome-local-dev");
  }

  /** schema-per-service：集成测试用独立 schema（如 svc_project_it），与开发数据互不污染。 */
  public static String jdbcUrl(String schema) {
    // stringtype=unspecified：MyBatis 以 String 参数写 jsonb 列所需（由 PG 服务端推断参数类型）
    return "jdbc:postgresql://%s:%d/%s?currentSchema=%s&stringtype=unspecified"
        .formatted(host(), port(), database(), schema);
  }

  /** TCP 可达性探测（不建 JDBC 连接，避免驱动依赖）。 */
  public static boolean isReachable() {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host(), port()), CONNECT_TIMEOUT_MS);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private static String envOr(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }
}
