-- svc_project 首批表（对齐文档 §5.1，V1.5：项目唯一真相）。
-- 数据纪律（技术架构 §6.4）：主键 ULID 字符串；时间戳 UTC timestamptz；created_at/updated_at 全表必备；
-- 软删统一 deleted_at；枚举存字符串（UPPER_SNAKE，与 Java 枚举逐字一致）。
-- schema-per-service：禁止跨 schema 外键与 join（技术架构 §2.5），本文件不建任何 FK。

CREATE SCHEMA IF NOT EXISTS svc_project;

-- 项目聚合入口：process_version 建项时固化（D10）；current_milestone 只由里程碑引擎迁移。
CREATE TABLE projects (
    id                varchar(26)  PRIMARY KEY,
    user_id           varchar(64)  NOT NULL,
    floorplan_ref     varchar(255),
    process_version   varchar(32)  NOT NULL,
    current_milestone varchar(32)  NOT NULL,
    status            varchar(32)  NOT NULL,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    deleted_at        timestamptz
);

CREATE INDEX idx_projects_user_id ON projects (user_id);

-- 槽位真相（吸收原 svc_design.facts）：status = 认知状态六值
-- （OBSERVED|INFERRED|PROPOSED|USER_CONFIRMED|MEASURED|VERIFIED，CognitiveState 逐字一致）；
-- stage = 槽位落库时项目所处里程碑。(project_id, slot_key) 幂等 upsert 的唯一键。
CREATE TABLE slots (
    id              varchar(26)      PRIMARY KEY,
    project_id      varchar(26)      NOT NULL,
    slot_key        varchar(64)      NOT NULL,
    value           text             NOT NULL,
    status          varchar(32)      NOT NULL,
    source_event_id varchar(128),
    confidence      double precision NOT NULL,
    stage           varchar(32)      NOT NULL,
    created_at      timestamptz      NOT NULL DEFAULT now(),
    updated_at      timestamptz      NOT NULL DEFAULT now(),
    deleted_at      timestamptz,
    CONSTRAINT uk_slots_project_slot_key UNIQUE (project_id, slot_key)
);

-- ArtifactRegistry：只持产物引用与生成参数血缘（storage_url 指向 OSS），不持产物本体。
-- status：GENERATED|PRESENTED|CONFIRMED|REJECTED（ArtifactStatus 逐字一致）。
-- view_spec_version / depends_on 为 §5.1 预留列，domain 模型随首个用例补齐。
CREATE TABLE artifacts (
    id                varchar(26)  PRIMARY KEY,
    project_id        varchar(26)  NOT NULL,
    milestone         varchar(32)  NOT NULL,
    artifact_type     varchar(64)  NOT NULL,
    version           int          NOT NULL,
    storage_url       text,
    gen_params        jsonb,
    lineage           jsonb,
    status            varchar(32)  NOT NULL,
    view_spec_version varchar(32),
    depends_on        jsonb,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    deleted_at        timestamptz
);

CREATE INDEX idx_artifacts_project_id ON artifacts (project_id);

-- 生成任务业务真相：执行、重试、心跳、超时语义全部在 Temporal，此表只记业务事实；
-- status：PENDING|RUNNING|COMPLETED|FAILED（GenerationTaskStatus 逐字一致）；artifact_id 完成后回填。
CREATE TABLE generation_tasks (
    id             varchar(26) PRIMARY KEY,
    project_id     varchar(26) NOT NULL,
    task_type      varchar(64) NOT NULL,
    input_snapshot jsonb,
    status         varchar(32) NOT NULL,
    artifact_id    varchar(26),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    deleted_at     timestamptz
);

CREATE INDEX idx_generation_tasks_project_id ON generation_tasks (project_id);

-- 修订记录：同项目同里程碑的记录数 = 已用修订轮数（修订预算判定依据）；
-- directive = 结构化修订指令 {target, dimension, direction}（chat 受限映射产物，本服务不理解自然语言）。
CREATE TABLE revision_log (
    id         varchar(26) PRIMARY KEY,
    project_id varchar(26) NOT NULL,
    milestone  varchar(32) NOT NULL,
    round_no   int         NOT NULL,
    directive  jsonb       NOT NULL,
    task_id    varchar(26),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);

CREATE INDEX idx_revision_log_project_milestone ON revision_log (project_id, milestone);

-- UserDecisions：确认/否决/里程碑进入（DecisionType：CONFIRM|REJECT|MILESTONE_ENTER 逐字一致），
-- source_event_id = 来源事件引用（幂等与审计锚点）。
CREATE TABLE decisions (
    id              varchar(26)  PRIMARY KEY,
    project_id      varchar(26)  NOT NULL,
    decision_type   varchar(32)  NOT NULL,
    milestone       varchar(32),
    artifact_id     varchar(26),
    source_event_id varchar(128),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    deleted_at      timestamptz
);

CREATE INDEX idx_decisions_project_id ON decisions (project_id);

-- Scene Graph（JSONB），绑定 deep revision（revision_log_id 引用修订记录）；结构随深化设计用例细化。
CREATE TABLE scenes (
    id              varchar(26) PRIMARY KEY,
    project_id      varchar(26) NOT NULL,
    milestone       varchar(32),
    scene_graph     jsonb       NOT NULL,
    revision_log_id varchar(26),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz
);

CREATE INDEX idx_scenes_project_id ON scenes (project_id);

-- 确认清单与深度提问：status 存字符串（如 OPEN|ANSWERED），词表随确认闭环用例固化。
CREATE TABLE open_questions (
    id              varchar(26)  PRIMARY KEY,
    project_id      varchar(26)  NOT NULL,
    milestone       varchar(32),
    question        text         NOT NULL,
    status          varchar(32)  NOT NULL DEFAULT 'OPEN',
    source_event_id varchar(128),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    deleted_at      timestamptz
);

CREATE INDEX idx_open_questions_project_id ON open_questions (project_id);

-- 本地事务 + outbox 发事件（对齐 2.6 纪律）：业务写与事件写同一事务落本表，
-- 中继投递 RocketMQ 后回填 published_at（先建表不接 MQ）。event_type = CloudEvents type。
CREATE TABLE outbox (
    id             varchar(26)  PRIMARY KEY,
    aggregate_type varchar(64)  NOT NULL,
    aggregate_id   varchar(26)  NOT NULL,
    event_type     varchar(128) NOT NULL,
    payload        jsonb        NOT NULL,
    occurred_at    timestamptz  NOT NULL DEFAULT now(),
    published_at   timestamptz,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    deleted_at     timestamptz
);

CREATE INDEX idx_outbox_unpublished ON outbox (occurred_at) WHERE published_at IS NULL;
