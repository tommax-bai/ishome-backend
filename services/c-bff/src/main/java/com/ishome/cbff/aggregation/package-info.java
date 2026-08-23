/**
 * aggregation 层——编排多个下游 SDK 调用、裁剪拼装端侧 DTO。
 *
 * <p>BFF 无业务规则：出现业务规则=分层泄漏，下沉到域服务（规范 §1.2，ArchUnit 强制无库无事务）。 端差异收口在 BFF（变化轴 10），不进域服务。
 */
package com.ishome.cbff.aggregation;
