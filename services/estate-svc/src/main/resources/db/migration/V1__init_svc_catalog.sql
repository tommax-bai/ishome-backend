-- svc_catalog 首批表：家具品类闭集 + 家具资产尺寸表。
--
-- ── 这两张表在整条线的哪一段 ──────────────────────────────────────────────────────
-- 业主发户型图 → 出三张图 → 确认需求 → **摆家具** → 出效果图。「摆家具」＝布局求解
-- plan-layout-solve（genpipe-worker，确定性求解），它要问的是"这个品类的家具多大"——本表之前
-- 系统里没有任何地方能回答：尺寸散在 render3d 的代码常量（furnish_mock.py）与测试夹具里，
-- 归属域是错的。**读表的是布局求解，不是三维渲染**：渲染拿的是求解产出的 FurnishingPlacement
-- （位置与尺寸都已定死），它不查资产表。
--
-- ── 落点 ─────────────────────────────────────────────────────────────────────
-- catalog 是独立逻辑域、物理落 estate 库的 svc_catalog schema（《架构对齐》§3.3，2026-08 已裁；
-- application.yml 的 flyway.schemas 早已声明，今天之前零表零迁移）。schema 独立＝将来可整体拆走。
-- schema 名经 Flyway placeholder ${catalog_schema} 注入（正式=svc_catalog，集成测试=svc_catalog_it），
-- 照 project-svc V2 注入 rulebook schema 名的先例——IT 的 clean→migrate 不触碰正式库。
-- （占位符名不在本注释里写第二遍：Flyway 连注释里的占位符也照替，写了就要求配一个值。）
--
-- ── 唯一真源 ──────────────────────────────────────────────────────────────────
-- 品类词与种子行的真源在 ishome-contracts：registries/furniture_categories.{md,json}（21 个品类）、
-- registries/furniture_assets.{md,json}（25 行）。**本文件不抄种子**——灌种子走
-- scripts/catalog/import_furniture_assets.py（读契约检出、幂等 upsert，契约改了重跑即重灌），
-- 同 rulebook 种子的既有做法（scripts/rulebook/import_seeds.py）。DDL 里写死 25 行等于把
-- "契约仓那份改了怎么办"这条路堵上。
--
-- ── 数据纪律（技术架构 §6.4）─────────────────────────────────────────────────────
-- 主键 ULID 字符串；时间戳 UTC timestamptz；created_at/updated_at 全表必备；软删统一 deleted_at；
-- 枚举存字符串。**禁止跨 schema 外键与 join**（技术架构 §2.5）——本文件的唯一一个 FK 在
-- svc_catalog 内部（资产 → 品类），不跨 schema，不违反该条。
--
-- ── 量纲：这里是米不是毫米，与《开发规范》§4.1 的"全链路毫米"有冲突，取米 ────────────────────
-- §4.1 定的是长度一律 _mm。本表取 _m，理由是字段形状由**跨仓契约**定死：
-- 《方案/深度设计入口与数据契约》§四 LayoutSolveRequest.candidates[] 逐字写着
-- width_m / depth_m / height_m，求解侧（Python）逐字读这三个名字，用户 2026-09-07 亦逐字拍过
-- （"档位给点值"）。改成毫米＝改跨仓协议，且会让库里的名字和线上传的名字对不上。
-- 量纲仍然入名（§4.1 的本意），单位由后缀 _m 明写，不存在裸 width。

CREATE SCHEMA IF NOT EXISTS ${catalog_schema};

-- ── 家具品类闭集（contracts registries/furniture_categories.json 的库内投影）────────────
-- 只增不改：品类词逐字进 FurnishingPlacement.category、进资产表、进需求清单，改名等同于改线上协议；
-- 某个品类再也不出现 ≠ 删掉它（故本表也走软删列而非物理删）。
-- **闭集约束的落点就是这张表**：资产表的 category 外键引用它，表外品类写不进去。
-- 不做 PG enum 也不把 21 个词抄成 CHECK IN 列表——那两种形态下"加一个品类"都要发迁移，
-- 而品类闭集的真源在契约仓、增补走灌种子（变体优先做成数据，《开发规范》§5.1 R2）。
CREATE TABLE ${catalog_schema}.furniture_categories (
    id         varchar(26)  PRIMARY KEY,
    category   varchar(64)  NOT NULL,
    semantics  text         NOT NULL,
    aliases    jsonb        NOT NULL DEFAULT '[]'::jsonb,
    first_seen text,
    layout_use text,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    CONSTRAINT uk_furniture_categories_category UNIQUE (category),
    -- 词形约束（contracts category_pattern 逐字）：ASCII 小写 kebab-case。
    -- 真数据里 tv-cabinet / coffee-table 本来就带连字符，故不套房间类别那条更严的单段规则。
    CONSTRAINT ck_furniture_categories_pattern CHECK (category ~ '^[a-z][a-z0-9-]*$')
);

COMMENT ON TABLE ${catalog_schema}.furniture_categories IS
    '家具品类闭集：唯一真源 ishome-contracts registries/furniture_categories.json（21 个品类，只增不改）；'
    '本表是它的库内投影，资产表 category 外键引用之';
COMMENT ON COLUMN ${catalog_schema}.furniture_categories.aliases IS
    '业主真说过的中文写法。**今天 21 行全空，这不是漏填**——需求清单那一步还没上线，一条真跑样本都没有；'
    '拿不到就说没有，不填猜的值。模型受限映射不靠本列（代码只校验结果 ∈ 闭集）';
COMMENT ON COLUMN ${catalog_schema}.furniture_categories.first_seen IS
    '首次出处：仓+文件+行号。不是"户型样本 sha256 + 产物路径"——本表的词来自代码常量与测试夹具，'
    '这两处都不是真跑样本；真跑来路今天一条都没有，有了要补登记';

-- ── 家具资产尺寸表（contracts registries/furniture_assets.json 的库内投影）────────────
-- 一行 = 一个品类的一个档位有多大。消费方＝布局求解：按 category + size_pref 取行，
-- 逐字装进 LayoutSolveRequest.candidates[]，求解器不改尺寸（"尺寸来自候选、位置由求解算出"）。
-- 后续路径（用户 2026-09-07 已拍）：拿真实家具数据更新**同一张表**——表结构不变，只改值、
-- 填 sku_ref、换 size_source、换来路；常规档位的行不删（真实家具覆盖不到的品类还要靠它兜底）。
CREATE TABLE ${catalog_schema}.furniture_assets (
    id          varchar(26)      PRIMARY KEY,
    asset_id    varchar(96)      NOT NULL,
    category    varchar(64)      NOT NULL,
    size_tier   varchar(16)      NOT NULL,
    width_m     double precision NOT NULL,
    depth_m     double precision NOT NULL,
    height_m    double precision NOT NULL,
    sku_ref     varchar(128),
    size_source varchar(32)      NOT NULL,
    provenance  text             NOT NULL,
    created_at  timestamptz      NOT NULL DEFAULT now(),
    updated_at  timestamptz      NOT NULL DEFAULT now(),
    deleted_at  timestamptz,
    -- 取行键：一个品类的一个档位只有一行（求解按 category + size_pref 取的就是它）
    CONSTRAINT uk_furniture_assets_category_tier UNIQUE (category, size_tier),
    -- 资产行标识全局唯一：已发布的 asset_id 只增不改
    CONSTRAINT uk_furniture_assets_asset_id UNIQUE (asset_id),
    -- 闭集约束：品类表外的词写不进来（同 schema 外键，不跨 schema）
    CONSTRAINT fk_furniture_assets_category FOREIGN KEY (category)
        REFERENCES ${catalog_schema}.furniture_categories (category),
    -- 档位闭集，逐字对上契约草案 requirements[].size_pref 的三档枚举。
    -- 这一条抄成 CHECK 而不建引用表：三档是契约里写死的枚举、不随数据增长，
    -- 与品类（只增、真源在契约仓、增补走灌种子）不是一类东西
    CONSTRAINT ck_furniture_assets_size_tier CHECK (size_tier IN ('small', 'standard', 'large')),
    -- 尺寸来源标闭集：今天只有 regular-tier 一个值，接真实家具数据那天加档走迁移
    CONSTRAINT ck_furniture_assets_size_source CHECK (size_source IN ('regular-tier')),
    -- asset_id 形态（contracts asset_id_pattern 逐字）：前缀 asset- 即命名空间
    CONSTRAINT ck_furniture_assets_asset_id_pattern CHECK (asset_id ~ '^asset-[a-z0-9][a-z0-9-]*$'),
    -- **命名禁纯序号**（红线）：上面那条只管字符集，asset-001 照样过得去。
    -- 语义命名的可机检判据 = asset- 之后至少有一个字母。列上带 CHECK 而不只靠灌库脚本自觉：
    -- 脚本拦的是"从契约灌"这一条路，列约束拦的是所有路（同 project-svc ck_parameters_value_kind 的取法）
    CONSTRAINT ck_furniture_assets_asset_id_semantic CHECK (asset_id ~ '^asset-.*[a-z]'),
    -- 点值、正数（用户 2026-09-07 拍"档位给点值"：**不存区间**，区间没有位置可放）
    CONSTRAINT ck_furniture_assets_dimensions_positive
        CHECK (width_m > 0 AND depth_m > 0 AND height_m > 0),
    -- 来路空不掉：19/25 行是"无定源"与"拟真填充"，如实存那句话。
    -- **没定源这件事不因为入库而消失**——空串等于把它盖掉，故连空串一起拦
    CONSTRAINT ck_furniture_assets_provenance_present CHECK (length(btrim(provenance)) > 0)
);

-- 按品类取候选（求解的主查询）由 uk_furniture_assets_category_tier 的前缀覆盖，不另建索引。

COMMENT ON TABLE ${catalog_schema}.furniture_assets IS
    '家具资产尺寸表：唯一真源 ishome-contracts registries/furniture_assets.json（25 行常规档位种子）；'
    '消费方＝布局求解 plan-layout-solve，**不是** render3d（渲染拿的是求解产出，尺寸已定死）';
COMMENT ON COLUMN ${catalog_schema}.furniture_assets.width_m IS
    '宽，点值，单位米（量纲入名）。字段名逐字对上 LayoutSolveRequest.candidates[]，不改毫米';
COMMENT ON COLUMN ${catalog_schema}.furniture_assets.sku_ref IS
    '型号引用，可空。**今天 25 行全空**——没有一行对应真货；接真实家具数据那天填';
COMMENT ON COLUMN ${catalog_schema}.furniture_assets.size_source IS
    '尺寸来源标（闭集）：regular-tier=常规住宅档位，不是真实家具、不是这户的实测/选定值。'
    '照 render3d models.py heights_source 的先例办——mock 与真值要在产出上分得开：'
    '一版布局是按真实家具算的还是按常规档位算的，看图的人有权知道';
COMMENT ON COLUMN ${catalog_schema}.furniture_assets.provenance IS
    '这一行的数出自哪儿（逐行出处）：抄依据注释 6 行 / 无定源 7 行 / 拟真填充无真跑来路 12 行。'
    '与 size_source 是两栏、不能互相代替：size_source 答"这批数是哪一类来源"（25 行全同），'
    '本列答"这一行的数出自哪儿"（分三种）；折进 size_source 就会把 19 行没有定源这件事盖掉';
