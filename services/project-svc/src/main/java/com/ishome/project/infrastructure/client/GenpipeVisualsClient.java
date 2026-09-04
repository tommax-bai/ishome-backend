package com.ishome.project.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ishome.project.domain.port.FloorplanVisualsDispatch;
import com.ishome.project.domain.port.FloorplanVisualsGateway;
import com.ishome.project.domain.port.VisualsDispatchException;
import com.ishome.project.domain.port.VisualsDispatchReceipt;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 编排侧三张图派发客户端（contracts openapi/genpipe.v1.yaml {@code POST /floorplan-visuals}）。
 *
 * <p>与 {@link GenpipeClient} 同一条通道形态（裁决④）、同一套重试口径：只跨瞬时故障、最多两次、同一 task_id 同一份 body；重试撞 409 ＝
 * 上一跳已打进去，按成功收。回流不在这一跳等——结论按报文里的回调地址送回。
 */
@Component
public class GenpipeVisualsClient implements FloorplanVisualsGateway {
  private static final String VISUALS_PATH = "/api/v1/genpipe/floorplan-visuals";
  private static final int MAX_ATTEMPTS = 2;

  private final RestClient restClient;

  public GenpipeVisualsClient(
      RestClient.Builder builder,
      @Value("${ishome.project.genpipe.base-url:http://127.0.0.1:8104}") String baseUrl,
      @Value("${ishome.project.genpipe.connect-timeout-ms:2000}") long connectTimeoutMs,
      @Value("${ishome.project.genpipe.read-timeout-ms:10000}") long readTimeoutMs) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
    requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
    this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
  }

  @Override
  public VisualsDispatchReceipt dispatch(FloorplanVisualsDispatch dispatch) {
    VisualsRequest body =
        new VisualsRequest(
            dispatch.taskId(),
            dispatch.floorplanObjectKey(),
            dispatch.buildingAreaSqm(),
            dispatch.floorAreaRatioPercent(),
            dispatch.resultCallbackUrl());
    RuntimeException lastFailure = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        VisualsReceipt received =
            restClient.post().uri(VISUALS_PATH).body(body).retrieve().body(VisualsReceipt.class);
        if (received == null) {
          throw new VisualsDispatchException(
              dispatch.taskId(), "编排侧回了 2xx 但没有回执体：" + dispatch.taskId());
        }
        return new VisualsDispatchReceipt(
            dispatch.taskId(), nullToEmpty(received.workflowId()), nullToEmpty(received.runId()));
      } catch (HttpClientErrorException.Conflict conflict) {
        if (attempt == 1) {
          throw new VisualsDispatchException(
              dispatch.taskId(), "编排侧报同一 task_id 已在飞，而这是首次派发：" + dispatch.taskId(), conflict);
        }
        return new VisualsDispatchReceipt(dispatch.taskId(), "", "");
      } catch (HttpClientErrorException rejected) {
        // 4xx（409 之外）：报文不合契约，重试也不会变好
        throw new VisualsDispatchException(
            dispatch.taskId(),
            "编排侧拒收三张图派发（" + rejected.getStatusCode() + "）：" + rejected.getResponseBodyAsString(),
            rejected);
      } catch (ResourceAccessException | HttpServerErrorException transientFailure) {
        lastFailure = transientFailure;
      }
    }
    throw new VisualsDispatchException(
        dispatch.taskId(), "三张图派发失败，已重试 " + MAX_ATTEMPTS + " 次：" + dispatch.taskId(), lastFailure);
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  /** 派发请求体：字段名逐字对齐 contracts genpipe.v1 floorplan_visuals_spec（snake_case）；空值不发。 */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record VisualsRequest(
      @JsonProperty("task_id") String taskId,
      @JsonProperty("floorplan_object_key") String floorplanObjectKey,
      @JsonProperty("building_area_sqm") Double buildingAreaSqm,
      @JsonProperty("floor_area_ratio_percent") Double floorAreaRatioPercent,
      @JsonProperty("result_callback_url") String resultCallbackUrl) {}

  private record VisualsReceipt(
      @JsonProperty("workflow_id") String workflowId, @JsonProperty("run_id") String runId) {}
}
