/**
 * 版本化流程定义——纯数据（D10：配置只放数据，逻辑归服务）。
 *
 * <p>completion_criteria 仅允许简单谓词（结构化谓词对象：slot 已达某认知状态、artifact 已达某状态）， 禁止发明表达式语法；判据需要真逻辑时做成
 * project-svc 接口。布尔求值在 {@link com.ishome.project.domain.MilestoneCompletionPolicy}，本包对象零行为。
 *
 * <p>单一来源双消费（对齐文档 §2.2）：project 消费判据/on_enter 编排/修订预算；chat 消费槽位 schema/修订维度词表/动作白名单——经 {@code GET
 * /api/v1/process-definitions/{version}} 权威分发。
 */
package com.ishome.project.domain.definition;
