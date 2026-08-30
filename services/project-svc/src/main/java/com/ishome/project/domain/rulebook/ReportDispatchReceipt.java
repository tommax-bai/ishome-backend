package com.ishome.project.domain.rulebook;

/**
 * 成文线派发回执：编排侧收下这一份报告后回的定址信息（图 v0.2 §2）。
 *
 * <p>**它不是状态**。派发是"启动即返回"——成文是一次短 run，结论经回流写回里程碑，不在请求内等待 （规则 8.1：状态真相不在编排侧，禁第二台状态机）。本记录只够拿去
 * Temporal 里定位那一次 run， 求值线不据此判断报告成没成。
 *
 * <p>{@code workflowId} 与 {@code runId} 允许为空串：重试时命中"同一 report_id 已在飞"，编排侧只答冲突、 不回定址。此处**不照
 * report_id 自己拼一个 workflow id**——那条派生规则住在编排侧，抄过来就成了两处各写 一遍的同一条规则（单价投影已经踩过这个坑）。要定址就拿 {@code
 * reportId} 去编排侧查。
 */
public record ReportDispatchReceipt(String reportId, String workflowId, String runId) {}
