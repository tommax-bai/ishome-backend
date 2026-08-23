# ishome-backend

《是我的家》业务域 monorepo：Java 21 + Spring Boot 3，Gradle 多模块。
基线文档在中控仓 `ishome`（技术架构方案 / 开发规范与代码分层 / 架构对齐文档）；本仓只落工具链与骨架，规范原文不在此复述。

> `{code}=ishome` 为待拍板工作默认值，见中控仓《落地假设与拍板清单》。

## 模块地图

```
services/
  identity-svc   多渠道身份绑定（首发飞书）+ JWT + 户型认领
  estate-svc     小区/户型资产、交付日历、搜索；含 catalog 模块（schema svc_catalog）
  content-svc    公开方案发布物（只存过机检门禁的内容）
  trade-svc      订单/支付/退款/对账（资金路径纪律：幂等键、金额 int 分、outbox）
  shelf-svc      选品池、联盟同步、归因
  channel-svc    IM 渠道网关：ChannelAdapter 插件（feishu/mock 起）+ 触达策略
  project-svc    项目唯一真相（V1.5）：里程碑引擎（事件驱动 checkCompletion）+ slot/artifact/task/revision + 修订预算 + 流程定义分发
  c-bff          C 端聚合（H5 指图时刻）——三层，无 domain
  admin-bff      管理端聚合 + 模板验收台——三层，无 domain
shared/
  kernel         共享领域原语 + ArchUnit 架构规则集（testFixtures 提供）
  starter        公共 Spring Boot 约定入驻点（保持薄）
```

生成域与会话域（genpipe/chat——V1.5：原 design-svc 拆为 chat-svc + project-svc，chat 归 `ishome-aipipe` 仓（Python），project-svc 在本仓）；契约唯一真源在 `ishome-contracts` 仓。

## 规范即工具链（本仓的执行面）

| 规范 | 执行 |
|---|---|
| DDD-lite 四层依赖方向、层内禁令（规范 §1.1） | `shared/kernel` ArchUnit 规则集，各服务 `ArchitectureTest` 挂 CI |
| BFF 无 domain/无库/无事务（§1.2） | 同上（BffArchRules） |
| 类后缀禁令、裸 DTO、量纲入名（§2.1/§2.2/§4.1） | Checkstyle 自定义正则（`config/checkstyle/`） |
| 渠道名三处白名单（§5.1 R4 / §6.2） | ArchUnit adapter 包隔离 + `scripts/check-channel-literals.sh` |
| 格式化 | Spotless（google-java-format），`./gradlew spotlessApply` |

## 常用命令

```bash
./gradlew build                    # 编译 + 全部质量门（CI 同款）
./gradlew spotlessApply            # 格式化
scripts/new-service.sh <domain>    # 新增服务：四层+ArchitectureTest+Flyway 目录自动生成并注册
```

本机需 JDK 21（Gradle toolchain 已配 foojay 自动供给，无 JDK 21 时自动下载）。

## contracts SDK 消费（占位）

跨服务调用只走 contracts 生成的 SDK client，禁手写（ArchUnit + 评审强制）。
TODO：`ishome-contracts` 首个 tag 后，经 GitHub Packages Maven 引入 `com.ishome:contracts-*`。

## 纪律备忘

- 事务边界只在 application 层；controller 不碰 Repository/Mapper（ArchUnit 拦截）。
- schema-per-service，禁止跨 schema 外键与 join；主键 ULID、UTC、软删、枚举存字符串、金额 int 分。
- 新增服务用脚手架，不手搭——分层靠模板生成，不靠人记。
- Conventional Commits，scope=服务名（如 `feat(estate): ...`）。

## 本地质量门（pre-push）

云端 CI 停用期间的本地把关：push 前自动跑本仓全套检查。新 clone 后执行一次 `git config core.hooksPath .githooks` 启用；紧急绕过用 `git push --no-verify`。
