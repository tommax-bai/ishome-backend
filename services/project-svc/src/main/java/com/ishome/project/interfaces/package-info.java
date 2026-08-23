/**
 * interfaces 层——入站适配：REST controller、MQ consumer、定时任务入口。
 *
 * <p>只依赖 application；禁止直调 repository / Mapper（事务边界与校验不可绕过，ArchUnit 强制）。 controller
 * 按资源复数名词命名（FloorplansController），MQ 入口 XxxConsumer（规范 §2.1）。
 */
package com.ishome.project.interfaces;
