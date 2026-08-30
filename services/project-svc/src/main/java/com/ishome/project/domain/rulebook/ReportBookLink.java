package com.ishome.project.domain.rulebook;

import java.net.URI;
import java.time.Instant;

/**
 * 一条能打开报告册的链接，以及它什么时候失效。
 *
 * <p>**链接自带有效期是形态不是限制**：私有桶不对外公开读，唯一的出口就是这种带签名与时限的地址。 业主拿到的是"这一份、这段时间内"的通行证，转发出去也不会变成永久公开。
 *
 * <p>当前没有"认人"这一步（触发条件写死＝开始接外部真实用户时），所以**拿到链接的人就能打开**—— 这是自测阶段如实的安全口径，不是遗漏。认人上线后这条链接改短、每次现签，
 * 那时"只有他能打开"才成立。
 */
public record ReportBookLink(URI url, Instant expiresAt) {}
