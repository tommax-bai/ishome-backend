package com.ishome.project.domain.port;

/**
 * 三张免费图派发 port：里程碑引擎建完 vision_image 任务后的唯一出口。
 *
 * <p>同 {@link ReportComposeGateway}：裁决④ 定了通道形态——走编排侧 HTTP 入口，不用 Java Temporal SDK 直连。
 * 本接口只说"把这张户型图派去出三张图"，队列 / workflow / 重试策略等编排词汇不出现在此。
 */
public interface FloorplanVisualsGateway {
  VisualsDispatchReceipt dispatch(FloorplanVisualsDispatch dispatch);
}
