package com.ishome.project.application;

import com.ishome.project.domain.rulebook.ReportDispatchReceipt;

/**
 * 一次报告派发的出参：铸出的 report_id + 编排侧回执。
 *
 * <p>**没有"报告成没成"这一项**——派发是启动即返回，成文结论经回流写回里程碑（规则 8.1 状态真相不在编排侧）。 这里回一个"报告好了"的字段就是在求值线里立第二台状态机。
 */
public record ReportDispatchResult(String reportId, ReportDispatchReceipt receipt) {}
