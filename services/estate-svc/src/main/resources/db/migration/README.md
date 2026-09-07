# Flyway 迁移目录（svc_estate + svc_catalog）

- 命名：`V{版本}__{描述}.sql`，版本单调递增。
- schema 两个，**同一个 Flyway 管**（application.yml `flyway.schemas`）：
  - `svc_estate`——本服务自己的域（小区/户型/交付日历，表未起）；迁移历史表落在这里（`default-schema`）。
  - `svc_catalog`——资产库，**逻辑独立域、物理落 estate**（《架构对齐》§3.3，2026-08 已裁）；
    schema 独立＝将来可整体拆走，升格独立服务的触发条件写死在对齐文档。
- schema-per-service：**禁止跨 schema 外键与 join**（技术架构 §2.5）——拆库不流血的全部秘密。
  `svc_catalog` 内部的外键（资产 → 品类闭集）不跨 schema，不在此禁之列。
- `svc_catalog` 的 schema 名经 Flyway placeholder 注入（正式 `svc_catalog`，集成测试 `svc_catalog_it`），
  照 project-svc 注入 rulebook schema 名的先例——IT 的 clean→migrate 不触碰正式库。
  **注释里也别写第二遍占位符**：Flyway 连注释里的占位符也照替，写了就要求配一个值。
- 数据纪律（技术架构 §6.4）：主键 ULID；时间戳 UTC；`created_at/updated_at` 全表必备；软删统一 `deleted_at`；枚举存字符串不存数字；金额 int 分。
- **种子不写进迁移**：家具品类闭集与资产尺寸的唯一真源在 `ishome-contracts/registries/`，
  由 `scripts/catalog/import_furniture_assets.py` 幂等灌入（契约改了重跑即重灌）。
  DDL 里抄死种子等于把"契约仓那份改了怎么办"这条路堵上。
- 存储选型为待拍板②的默认值（Postgres），改判时本目录方言随之调整。
