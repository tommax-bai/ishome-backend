package com.ishome.project.domain.port;

/**
 * 三张图派发的报文（contracts genpipe.v1 {@code floorplan_visuals_spec} 的业务侧形态）。
 *
 * <p>生成侧不知用户是谁：这里只有任务 id、对象键、两个数字与回调地址，没有任何身份字段。 {@code resultCallbackUrl} 由本服务注入——编排侧不知道业务侧在哪（规范
 * §1.0 向上通信只走回调）。 两个数字可空＝业主没给也没推。
 */
public record FloorplanVisualsDispatch(
    String taskId,
    String floorplanObjectKey,
    Double buildingAreaSqm,
    Double floorAreaRatioPercent,
    String resultCallbackUrl) {}
