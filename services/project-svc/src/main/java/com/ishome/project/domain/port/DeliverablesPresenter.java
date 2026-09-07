package com.ishome.project.domain.port;

/**
 * 产物呈现 port：链路单向的最后一跳（project 判定 → 事件 → 会话侧呈现），会话侧经渠道把产物发给业主。
 *
 * <p>实现走 contracts {@code ishome.design.v1.DesignService.PresentDeliverables}（gRPC 生成 stub）。 返回
 * true＝这一次真的发出去了；false＝会话侧按 delivery_id 幂等跳过（上一次已经发到业主手里了）， 事件同样收口、不再重投；抛异常＝没送到，事件留在 outbox 等下一轮中继。
 */
public interface DeliverablesPresenter {
  boolean present(DeliverablesPresentation presentation);
}
