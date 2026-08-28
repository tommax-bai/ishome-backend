package com.ishome.project.domain.rulebook;

/**
 * 被纪律拿掉的落点（规则 4.10"或隐藏该条目"）：**只留 id 与原因，不带值、不带 source、不带名称**—— 隐藏的意思就是内容不下发，成文线拿不到任何可写的东西。
 *
 * <p>为什么不并进 {@link GapRecord}：gap- 的语义是"求不出"（缺输入/无公式/空定义），是获取与实现回路的输入；
 * 本记录的语义是"求出来了但纪律不许发"，是核验回路的输入。两者合并会污染 gap- 回流信号，各自的下一步动作也不同 （gap- 去补公式或输入，withheld 去补外部依据把条目转正）。
 *
 * <p>它同时给成文线出口过检一个正面清单：卡片引用了这里的 id，打回理由是"该落点已按纪律隐藏"，不是"引用不存在"。
 */
public record WithheldAnchor(String lkpId, String basisTag, String reason) {}
