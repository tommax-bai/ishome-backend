package com.ishome.project.domain.port;

/**
 * 产物呈现 port：链路单向的最后一跳（project 判定 → 事件 → 会话侧呈现），会话侧经渠道把产物发给业主。
 *
 * <p>实现走 contracts {@code ishome.design.v1.DesignService.PresentDeliverables}（gRPC 生成 stub）。 返回
 * true＝会话侧确认已发出（重投命中幂等也算送达）；false / 抛异常＝这一次没送到，事件留在 outbox 等下一轮中继。
 */
public interface DeliverablesPresenter {
  boolean present(DeliverablesPresentation presentation);
}
