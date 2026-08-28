package com.ishome.project.domain.rulebook;

/**
 * 判官反例样例（规则 4.17 自迭代回路；图 v0.2 §3 出口过检·判官层）：挂在它所例示的那条 cr- 判据之下，**不新建第三类命名空间**——规则 4.10b
 * 归谬：样例是"用样例定义的纪律"，不是关于世界的知识断言（无对错可核、无外部源可挂），纪律的唯一形态是 check，正当性锚仍是该 check 的 {@code decidedBy}。
 *
 * <p>三件：{@code bad} 真跑里模型写出的原句（**只收观察到的样本，禁想象填充**，规范 v2.3 §12）、{@code why} 为什么它错、{@code fixed}
 * 合规写法长什么样。{@code fixed} 是给写作侧与回路看的，**不下发给判官**——判官只报编号不改写（把修好的答案递到判官手里，等于请它越权改写， 而判官与写手同源，改写即漂移）。
 *
 * <p>样例是文本不是阈值，与规则 4.10b"check 不得携带内容数值"不冲突：数值仍只经 {@code thresholdRefs} 引用 lkp- 参数。
 */
public record CheckExample(String bad, String why, String fixed) {}
