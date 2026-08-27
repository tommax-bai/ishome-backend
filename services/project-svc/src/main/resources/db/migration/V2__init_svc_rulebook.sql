-- svc_rulebook：知识资产库（规范 v2.2 规则 4.12）。project-svc 规则引擎模块持有；
-- schema 独立 = 将来可整体拆走。数据纪律同 V1：ULID 主键、UTC timestamptz、软删 deleted_at、
-- 无跨 schema FK。枚举存字符串（与规范词表逐字一致：calibration = draft|calibrated|needs_review）。
-- 表族 = 规则 4.9 五形态（rules/parameters/attributes/templates/vocabularies）+ personas（规则 4.13）
--       + checks（规则 4.10b：纪律唯一形态，不进 calibration 状态机，锚 decided_by）
--       + releases（规则 4.12：域级不可变发布快照，运行时唯一读取面）。
-- asset_id = 语义标识符（规则 1.7：lkp-*/rule-*/attr-*/cr-*/persona-*），(asset_id, version) 唯一。
-- schema 名经 Flyway placeholder ${rulebook_schema} 注入：正式=svc_rulebook（application.yml），
-- 集成测试=svc_rulebook_it（PostgresIntegrationTestSupport）——IT 的 clean→migrate 不触碰正式库。

CREATE SCHEMA IF NOT EXISTS ${rulebook_schema};

-- rule 形态：触发 → 条目（规范 §4.1 三层三触发）
CREATE TABLE ${rulebook_schema}.rules (
    id             varchar(26)  PRIMARY KEY,
    asset_id       varchar(96)  NOT NULL,
    domain         varchar(32)  NOT NULL,
    layer          varchar(32)  NOT NULL,          -- tier-mandatory|tier-practice|tier-personal
    trigger        jsonb        NOT NULL,          -- {type, layout_feature?, question_id?, answer_match?}
    content        text         NOT NULL,
    rationale      text,
    severity       varchar(16)  NOT NULL,          -- mandatory|recommended|optional
    point_spec     jsonb,
    calibration    varchar(16)  NOT NULL DEFAULT 'draft',
    source         text,
    source_pending text,
    consumers      jsonb        NOT NULL DEFAULT '[]',
    version        int          NOT NULL DEFAULT 1,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    deleted_at     timestamptz,
    CONSTRAINT uk_rules_asset_version UNIQUE (asset_id, version)
);
CREATE INDEX idx_rules_domain ON ${rulebook_schema}.rules (domain);

-- parameter 形态：计算依据与数值区间（lkp-* 落点求值的数据源）
CREATE TABLE ${rulebook_schema}.parameters (
    id             varchar(26)  PRIMARY KEY,
    asset_id       varchar(96)  NOT NULL,
    domain         varchar(32)  NOT NULL,
    name           varchar(128) NOT NULL,
    number_class   varchar(16)  NOT NULL,          -- 数字三分法：position|selection|analysis（规则 2.3）
    value          jsonb,                          -- 区间/单值/结构化值
    formula        text,
    unit           varchar(32),
    linked         jsonb,                          -- 关联参数 asset_id 列表
    calibration    varchar(16)  NOT NULL DEFAULT 'draft',
    source         text,
    source_pending text,
    acquired       varchar(64),                    -- 获取回路 run 标识
    consumers      jsonb        NOT NULL DEFAULT '[]',
    version        int          NOT NULL DEFAULT 1,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    deleted_at     timestamptz,
    CONSTRAINT uk_parameters_asset_version UNIQUE (asset_id, version)
);
CREATE INDEX idx_parameters_domain ON ${rulebook_schema}.parameters (domain);

-- attribute 形态：entity_type + JSONB 属性包承载异构实体，不按实体类建表（规则 4.12）。
-- effective_* 提升为实体列：过期降档检查（cr-budget-stale-price 类）需要可索引的时效。
CREATE TABLE ${rulebook_schema}.attributes (
    id             varchar(26)  PRIMARY KEY,
    asset_id       varchar(96)  NOT NULL,
    domain         varchar(32)  NOT NULL,
    entity_type    varchar(32)  NOT NULL,          -- material|color|storage_item|work_item…
    name           varchar(128) NOT NULL,
    props          jsonb        NOT NULL,
    confidence     varchar(16),                    -- high|medium|low（决定区间宽度）
    effective_from date,
    effective_to   date,                           -- 时效资产必填（单价库强制，规则 4.10）
    calibration    varchar(16)  NOT NULL DEFAULT 'draft',
    source         text,
    source_2       text,                           -- 第二源（交叉验证，规则 4.16）
    source_pending text,
    note           text,
    consumers      jsonb        NOT NULL DEFAULT '[]',
    version        int          NOT NULL DEFAULT 1,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    deleted_at     timestamptz,
    CONSTRAINT uk_attributes_asset_version UNIQUE (asset_id, version)
);
CREATE INDEX idx_attributes_domain_entity ON ${rulebook_schema}.attributes (domain, entity_type);

-- template 形态：gen-assembled 拼装句式。规则 4.17：由自迭代回路自产，人不写——建表先行，冷启动期允许
-- "人驱动 AI 起草→种子集回归→观察态"路径灌入（规则 4.19），status 区分观察态。
CREATE TABLE ${rulebook_schema}.templates (
    id             varchar(26)  PRIMARY KEY,
    asset_id       varchar(96)  NOT NULL,
    domain         varchar(32)  NOT NULL,
    slot_text      text         NOT NULL,          -- 含 {slot} 占位的句式
    slots          jsonb        NOT NULL DEFAULT '[]',
    status         varchar(16)  NOT NULL DEFAULT 'observing',  -- observing|active|retired（观察态机制，规则 4.17）
    calibration    varchar(16)  NOT NULL DEFAULT 'draft',
    source         text,
    consumers      jsonb        NOT NULL DEFAULT '[]',
    version        int          NOT NULL DEFAULT 1,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    deleted_at     timestamptz,
    CONSTRAINT uk_templates_asset_version UNIQUE (asset_id, version)
);
CREATE INDEX idx_templates_domain ON ${rulebook_schema}.templates (domain);

-- vocabulary 形态：受控词汇（规格词表/修订维度/色彩命名/禁词表公共部分）
CREATE TABLE ${rulebook_schema}.vocabularies (
    id             varchar(26)  PRIMARY KEY,
    asset_id       varchar(96)  NOT NULL,
    domain         varchar(32)  NOT NULL,          -- 跨域词表 domain='cross'
    kind           varchar(32)  NOT NULL,          -- spec_word|banned_term|revision_dim|color_name…
    terms          jsonb        NOT NULL,
    calibration    varchar(16)  NOT NULL DEFAULT 'draft',
    source         text,
    consumers      jsonb        NOT NULL DEFAULT '[]',
    version        int          NOT NULL DEFAULT 1,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    deleted_at     timestamptz,
    CONSTRAINT uk_vocabularies_asset_version UNIQUE (asset_id, version)
);

-- persona 资产包（规则 4.13 四件）：一域一份，随域 release 发布
CREATE TABLE ${rulebook_schema}.personas (
    id               varchar(26)  PRIMARY KEY,
    asset_id         varchar(96)  NOT NULL,
    domain           varchar(32)  NOT NULL,
    identity         text         NOT NULL,
    judgment_samples jsonb        NOT NULL DEFAULT '[]',
    assertion_budget jsonb        NOT NULL DEFAULT '[]',
    banned_terms     jsonb        NOT NULL DEFAULT '{}',
    calibration      varchar(16)  NOT NULL DEFAULT 'draft',
    source           text,
    consumers        jsonb        NOT NULL DEFAULT '[]',
    version          int          NOT NULL DEFAULT 1,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    deleted_at       timestamptz,
    CONSTRAINT uk_personas_asset_version UNIQUE (asset_id, version)
);

-- check 形态（规则 4.10b）：纪律的唯一存在形式。不进 calibration 状态机（无 calibration 列）；
-- 正当性锚 = decided_by（NOT NULL）。不携带内容数值：数值阈值只能经 threshold_refs 引用 parameters.asset_id。
CREATE TABLE ${rulebook_schema}.checks (
    id             varchar(26)  PRIMARY KEY,
    asset_id       varchar(96)  NOT NULL,
    domain         varchar(32)  NOT NULL,          -- 跨域机检 domain='cross'（灌库/发布时物化进各域 release）
    check_type     varchar(32)  NOT NULL,          -- regex_deny|regex_require_annotation|field_deny|count_max|cross_field|presentation|presence_require|threshold_all
    scope          jsonb        NOT NULL DEFAULT '[]',
    pattern        text,
    requirement    text,
    message        text         NOT NULL,
    decided_by     text         NOT NULL,          -- 裁决记录锚（规范条文号+日期）
    threshold_refs jsonb        NOT NULL DEFAULT '[]',  -- 引用的 lkp-* asset_id（如 max_from）
    version        int          NOT NULL DEFAULT 1,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    deleted_at     timestamptz,
    CONSTRAINT uk_checks_asset_version UNIQUE (asset_id, version)
);

-- 域级发布快照（规则 4.12）：不可变；运行时求值只读本表。snapshot = 该域全部 calibrated 资产
-- + 物化的跨域 check/词表的完整 JSON。release_tag 形如 'lighting@v3'。发布后行不再 UPDATE。
CREATE TABLE ${rulebook_schema}.releases (
    id          varchar(26)  PRIMARY KEY,
    domain      varchar(32)  NOT NULL,
    release_tag varchar(64)  NOT NULL,
    snapshot    jsonb        NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_releases_tag UNIQUE (release_tag)
);
CREATE INDEX idx_releases_domain ON ${rulebook_schema}.releases (domain);
