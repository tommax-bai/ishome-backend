package com.ishome.project.domain.port;

import com.ishome.project.domain.rulebook.ReportDataPackage;
import com.ishome.project.domain.rulebook.ReportDispatchReceipt;
import java.util.List;

/**
 * 成文线派发 port：报告数据包出求值线之后的唯一出口（图 v0.2 §2 两条流水线的接缝）。
 *
 * <p>裁决④ 定了通道形态：**走编排侧的 HTTP 入口，不用 Java Temporal SDK 直连**——直连会把里程碑引擎裁决 刚收缩掉的 Temporal 依赖放回 Java
 * 服务。本接口因此只说"把这份包派出去"，不出现队列、workflow、 重试策略等编排词汇；那些是 infrastructure 的实现细节。
 *
 * <p>{@code domains} 单独传而不从包里读：编排侧按它 fan-out 派发单元，包内 {@code domains} 是求值范围，
 * 两者可以不等（一次求值的域多于本次要成文的域）。
 */
public interface ReportComposeGateway {

  ReportDispatchReceipt dispatch(
      String reportId, List<String> domains, ReportDataPackage dataPackage);
}
