# Flyway 迁移目录（svc_shelf）

- 命名：`V{版本}__{描述}.sql`，版本单调递增。
- schema：`svc_shelf`（schema-per-service；禁止跨 schema 外键与 join——拆库不流血的全部秘密）。
- 数据纪律（技术架构 §6.4）：主键 ULID；时间戳 UTC；`created_at/updated_at` 全表必备；软删统一 `deleted_at`；枚举存字符串不存数字；金额 int 分。
- 存储选型为待拍板②的默认值（Postgres），改判时本目录方言随之调整。
