/**
 * interfaces 层——controller 按端侧场景组织（指图时刻、项目时间线、分享卡片），不按下游服务组织。
 *
 * <p>REST：/api/v1 复数资源、JSON 字段 snake_case 端到端（技术架构 §6.1）。 每次 H5 交互结果须回写一条摘要消息到聊天线程（经
 * design-svc，对齐文档 §6.1 配套纪律）。
 */
package com.ishome.cbff.interfaces;
