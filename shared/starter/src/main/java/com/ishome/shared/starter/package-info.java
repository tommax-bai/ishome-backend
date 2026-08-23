/**
 * 公共 Spring Boot 约定入驻点（保持薄）：统一错误信封（{DOMAIN}_{3位} + request_id，注册表在 contracts 仓）、请求日志、outbox
 * 发送支撑等，随首个真实用例进入，不预先堆抽象（规范 §五 R1）。
 */
package com.ishome.shared.starter;
