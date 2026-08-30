package com.ishome.project.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ishome.project.domain.port.ReportComposeGateway;
import com.ishome.project.domain.rulebook.ReportDataPackage;
import com.ishome.project.domain.rulebook.ReportDispatchException;
import com.ishome.project.domain.rulebook.ReportDispatchReceipt;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 编排侧（genpipe-svc）HTTP 客户端：报告数据包出求值线的落点（裁决④）。
 *
 * <p>本仓第一个出站 HTTP 调用。用 {@code RestClient}（starter-web 自带，零新增依赖）；序列化沿用 Spring 装配好的 ObjectMapper——包体是
 * camelCase（对齐 contracts {@code rulebook/report_data_package.schema.json}）， **外层请求体是
 * snake_case**（编排侧 pydantic 模型的字段名），两套命名同报文共存，是实测事实不是笔误。
 *
 * <p>重试只跨瞬时故障（连不上/读超时/5xx），**最多两次**：派发是"启动即返回"、下游只做启动动作，多试无非把 一次失败拖长——真正的重试语义在编排侧的 Temporal
 * 里，不在这一跳。重试用**同一个 report_id 同一份 body**， 幂等键握在重试的这一侧（裁决③）；因此重试撞上 409 = 上一次其实已经打进去了，按成功收。
 */
@Component
public class GenpipeClient implements ReportComposeGateway {

  /** 成文线派发路径（编排侧 {@code /api/v1/genpipe} 前缀下的 reports 资源）。 */
  private static final String COMPOSE_PATH = "/api/v1/genpipe/reports";

  private static final int MAX_ATTEMPTS = 2;

  /**
   * 出线报文的序列化口径：**这一跳自己定死，不吃环境里那个 ObjectMapper**。
   *
   * <p>真跑打脸：用默认 mapper 时 {@code evaluatedOn} 出线成 {@code [2026,8,29]}——contracts schema 要的是 ISO
   * 日期串。跨仓契约面的字段形态不该随 {@code spring.jackson.*} 一改就漂：那是全局配置，改它的人 看不见这条线；漏发现的形态是下游整包解析失败，离改动很远。
   */
  private static final ObjectMapper WIRE_MAPPER =
      JsonMapper.builder()
          .addModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .build();

  private final RestClient restClient;

  public GenpipeClient(
      RestClient.Builder builder,
      @Value("${ishome.project.genpipe.base-url:http://127.0.0.1:8104}") String baseUrl,
      @Value("${ishome.project.genpipe.connect-timeout-ms:2000}") long connectTimeoutMs,
      @Value("${ishome.project.genpipe.read-timeout-ms:10000}") long readTimeoutMs) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
    requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
    this.restClient =
        builder
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .messageConverters(
                converters -> {
                  converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                  converters.add(0, new MappingJackson2HttpMessageConverter(WIRE_MAPPER));
                })
            .build();
  }

  @Override
  public ReportDispatchReceipt dispatch(
      String reportId, List<String> domains, ReportDataPackage dataPackage) {
    ComposeRequest body = new ComposeRequest(reportId, domains, dataPackage);
    RuntimeException lastFailure = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        return receiptOf(reportId, post(body));
      } catch (HttpClientErrorException.Conflict conflict) {
        if (attempt == 1) {
          throw new ReportDispatchException(
              reportId, "编排侧报同一 report_id 已在飞，而这是首次派发：" + reportId, conflict);
        }
        // 重试撞 409：上一跳其实已经打进去了，只是回执没回来。编排侧不随冲突回定址，故回执留空。
        return new ReportDispatchReceipt(reportId, "", "");
      } catch (ResourceAccessException | HttpServerErrorException transientFailure) {
        lastFailure = transientFailure;
      }
    }
    throw new ReportDispatchException(
        reportId, "成文线派发失败，已重试 " + MAX_ATTEMPTS + " 次：" + reportId, lastFailure);
  }

  private ComposeReceipt post(ComposeRequest body) {
    return restClient.post().uri(COMPOSE_PATH).body(body).retrieve().body(ComposeReceipt.class);
  }

  private static ReportDispatchReceipt receiptOf(String reportId, ComposeReceipt received) {
    if (received == null) {
      throw new ReportDispatchException(reportId, "编排侧回了 2xx 但没有回执体：" + reportId);
    }
    return new ReportDispatchReceipt(
        reportId, nullToEmpty(received.workflowId()), nullToEmpty(received.runId()));
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  /**
   * 派发请求体：字段名逐字对齐编排侧 pydantic 模型（snake_case）。
   *
   * <p>{@code package} 是 Java 关键字，字段只能另起名字、靠 {@code @JsonProperty} 对上线上字段名—— 线上名以编排侧为准，Java
   * 这边叫什么都行。{@code max_rewrites}/{@code queues} 不传：前者是图 v0.2 §3 的设计定数（≤2
   * 轮，不许私改），后者生产调用方一律不覆写，两者都用编排侧默认。
   */
  private record ComposeRequest(
      @JsonProperty("report_id") String reportId,
      @JsonProperty("domains") List<String> domains,
      @JsonProperty("package") ReportDataPackage dataPackage) {}

  /** 编排侧回执（snake_case）。 */
  private record ComposeReceipt(
      @JsonProperty("workflow_id") String workflowId, @JsonProperty("run_id") String runId) {}
}
