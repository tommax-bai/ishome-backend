/**
 * infrastructure 层——repository 实现（XxxRepositoryImpl，内部调 XxxMapper）、contracts 生成的 外部服务
 * client（禁手写客户端）、MQ producer、缓存。
 *
 * <p>实现 domain 的接口（依赖方向 infrastructure → domain），不得被其他层依赖（ArchUnit 强制）。
 */
package com.ishome.channel.infrastructure;
