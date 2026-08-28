/**
 * 规则引擎模块（求值线，图 v0.2 §2）：读 svc_rulebook 域级 release 不可变快照，对 lkp- 落点做确定性求值， 产出报告数据包（成文线 input_snapshot
 * 的前身）。
 *
 * <p>硬性纪律（图 v0.2 §0）：数字不由 LLM 决定——一切落点在任务派发前求值完毕；同输入同输出（规则 8.2 可重放）；输入为 slots 派生的匿名结构，无任何用户标识；查不到 →
 * gap- 记录，不阻塞。 运行时只读 release（规则 4.12），工作态六表不进本模块。
 *
 * <p>降档门禁也在本模块执行（规则 4.10）：产物权益档由调用方传入（{@link
 * com.ishome.project.domain.rulebook.ArtifactEntitlement}），每个落点判一次呈现档位（{@link
 * com.ishome.project.domain.rulebook.AnchorPresentationPolicy}），判为隐藏的**值根本不下发**。
 * 纪律执行在生产侧、判定结果随包下发到消费侧——成文线不需要也不允许自行裁量。
 */
package com.ishome.project.domain.rulebook;
