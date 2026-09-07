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
-- schema 名经 Flyway placeholder 注入（正式=svc_catalog，集成测试=svc_catalog_it），
-- 照 project-svc V2 注入 rulebook schema 名的先例——IT 的 clean→migrate 不触碰正式库。
-- （占位符名不在本注释里写第二遍：Flyway 连注释里的占位符也照替，写了就要求配一个值。）
--
-- ── 唯一真源 ──────────────────────────────────────────────────────────────────
-- 品类词与种子行的真源在 ishome-contracts：registries/furniture_categories.{md,json}（21 个品类，
-- 未退役 20 + 退役 1）、registries/furniture_assets.{md,json}（24 行）。**本文件不抄种子**——
-- 灌种子走 scripts/catalog/import_furniture_assets.py（读契约检出、幂等 upsert，契约改了重跑即重灌），
-- 同 rulebook 种子的既有做法（scripts/rulebook/import_seeds.py）。DDL 里写死那 24 行等于把
-- "契约仓那份改了怎么办"这条路堵上。
--
-- ── 数据纪律（技术架构 §6.4）─────────────────────────────────────────────────────
-- 主键 ULID 字符串；时间戳 UTC timestamptz；created_at/updated_at 全表必备；软删统一 deleted_at；
-- 枚举存字符串。**禁止跨 schema 外键与 join**（技术架构 §2.5）——本文件的外键都在 svc_catalog
-- 内部（资产 → 品类），不跨 schema，不违反该条。
--
-- ── 量纲：整数毫米，与《开发规范》§4.1 一致，无例外 ────────────────────────────────────
-- 尺寸三字段 width_mm / depth_mm / height_mm，**整数毫米**（用户裁决 2026-09-07"量纲统一毫米，
-- 家具资产表不例外"，原话"被都改成毫米吧"）。契约仓 24 行值一律 ×1000，无一例出现非整数毫米。
-- 本文件初版曾按米制建（width_m 等 double），理由是"字段名由跨仓契约定死、改毫米＝改协议"——
-- **该理由已被判定不成立**：那份 LayoutSolveRequest.candidates[] 是草案、零实现，没有协议可破，
-- 改的成本只是改草案（契约仓已同步改）。这条一并把本表对 §4.1 的偏离消掉了。
-- 点值不存区间（同日裁决"档位给点值"）；整数列同时把"半毫米"这种没有来源的精度挡在外面。

CREATE SCHEMA IF NOT EXISTS ${catalog_schema};

-- ── 家具品类闭集（contracts registries/furniture_categories.json 的库内投影）────────────
-- 只增不改：品类词逐字进 FurnishingPlacement.category、进资产表、进需求清单，改名等同于改线上协议；
-- 某个品类再也不出现 ≠ 删掉它。被推翻的品类走**退役写法**，不走删除（retired 列，见下）。
-- **闭集约束的落点就是这张表**：资产表的 category 外键引用它，表外品类写不进去。
-- 不做 PG enum 也不把 21 个词抄成 CHECK IN 列表——那两种形态下"加一个品类"都要发迁移，
-- 而品类闭集的真源在契约仓、增补走灌种子（变体优先做成数据，《开发规范》§5.1 R2）。
CREATE TABLE ${catalog_schema}.furniture_categories (
    id         varchar(26)  PRIMARY KEY,
    category   varchar(64)  NOT NULL,
    semantics  text         NOT NULL,
    aliases    jsonb        NOT NULL DEFAULT '[]'::jsonb,
    -- 退役告示：NULL＝在役；非 NULL＝已退役，内容是"指向哪几条 + 日期 + 理由"（契约逐字投影）。
    -- 不做成 boolean：只知道"退了"回答不了"那该用哪个"，而退役的全部用处就是把人引到替代条目上
    retired    text,
    -- 退役与否的布尔投影，**只为让外键引用得到**（见资产表 fk_furniture_assets_category）。
    -- GENERATED＝没有任何写入方能把它和 retired 写得不一致
    is_retired boolean GENERATED ALWAYS AS (retired IS NOT NULL) STORED,
    first_seen text,
    layout_use text,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    CONSTRAINT uk_furniture_categories_category UNIQUE (category),
    -- 复合唯一键：资产表的复合外键要引用它，才能把"退役品类不许有资产行"变成结构事实
    CONSTRAINT uk_furniture_categories_category_retirement UNIQUE (category, is_retired),
    -- 词形约束（contracts category_pattern 逐字）：ASCII 小写 kebab-case。
    -- 真数据里 tv-cabinet / coffee-table 本来就带连字符，故不套房间类别那条更严的单段规则。
    CONSTRAINT ck_furniture_categories_pattern CHECK (category ~ '^[a-z][a-z0-9-]*$'),
    -- 退役要说清退到哪儿：空串等于只说"废了"不说"改用什么"（《纪律·不许只说不做什么》）
    CONSTRAINT ck_furniture_categories_retired_present
        CHECK (retired IS NULL OR length(btrim(retired)) > 0)
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
COMMENT ON COLUMN ${catalog_schema}.furniture_categories.retired IS
    '退役告示（NULL=在役）：词被推翻时不删行、标退役并指向替代条目（只增不改）。'
    '今天唯一一条＝bathroom-fixture 已退役 → toilet/vanity/shower（用户裁决 2026-09-07 卫浴拆开）。'
    '退役的词不再进求解，但老产物仍解析得出';

-- ── 家具资产尺寸表（contracts registries/furniture_assets.json 的库内投影）────────────
-- 一行 = 一个品类的一个档位有多大。消费方＝布局求解：按 category + size_pref 取行，
-- 逐字装进 LayoutSolveRequest.candidates[]，求解器不改尺寸（"尺寸来自候选、位置由求解算出"）。
-- 后续路径（用户 2026-09-07 已拍）：拿真实家具数据更新**同一张表**——表结构不变，只改值、
-- 填 sku_ref、换 size_source、换来路；常规档位的行不删（真实家具覆盖不到的品类还要靠它兜底），
-- **删行的例外只有一种＝品类退役**，那时连同行一起撤。
CREATE TABLE ${catalog_schema}.furniture_assets (
    id          varchar(26) PRIMARY KEY,
    asset_id    varchar(96) NOT NULL,
    category    varchar(64) NOT NULL,
    -- 恒为 false，且 GENERATED＝谁都写不成 true。它存在的唯一理由是让下面那条复合外键
    -- 只能指到"在役"的品类行上——**退役品类零资产行由结构堵死**，不靠一个要人记得过滤的标记位
    -- （契约 furniture_assets.md 原话："用『没有行』结构堵死，比用一个要人记得看的标记位牢"）。
    -- 反向也一并被堵死：还留着资产行的品类退不了役——撤行必须先于退役，顺序与契约一致
    category_is_retired boolean NOT NULL GENERATED ALWAYS AS (false) STORED,
    size_tier   varchar(16) NOT NULL,
    width_mm    integer     NOT NULL,
    depth_mm    integer     NOT NULL,
    height_mm   integer     NOT NULL,
    sku_ref     varchar(128),
    size_source varchar(32) NOT NULL,
    provenance  text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted_at  timestamptz,
    -- 取行键：一个品类的一个档位只有一行（求解按 category + size_pref 取的就是它）
    CONSTRAINT uk_furniture_assets_category_tier UNIQUE (category, size_tier),
    -- 资产行标识全局唯一：已发布的 asset_id 不改名、不复用——一个 id 用过就归它，
    -- 哪怕那一行被撤下（先例：asset-bathroom-fixture-standard，2026-09-07 随卫浴拆开撤行作废）
    CONSTRAINT uk_furniture_assets_asset_id UNIQUE (asset_id),
    -- 闭集约束与退役约束一并落在这条复合外键上（同 schema 外键，不跨 schema）：
    -- 品类表里没有的词写不进来，标了退役的词也写不进来
    CONSTRAINT fk_furniture_assets_category FOREIGN KEY (category, category_is_retired)
        REFERENCES ${catalog_schema}.furniture_categories (category, is_retired),
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
    -- 点值、正整数毫米（用户 2026-09-07 拍"档位给点值"：**不存区间**；同日拍量纲统一毫米）
    CONSTRAINT ck_furniture_assets_dimensions_positive
        CHECK (width_mm > 0 AND depth_mm > 0 AND height_mm > 0),
    -- 来路空不掉：19/24 行是"无定源"与"拟真填充"，如实存那句话。
    -- **没定源这件事不因为入库而消失**——空串等于把它盖掉，故连空串一起拦
    CONSTRAINT ck_furniture_assets_provenance_present CHECK (length(btrim(provenance)) > 0)
);

-- 按品类取候选（求解的主查询）由 uk_furniture_assets_category_tier 的前缀覆盖，不另建索引。

COMMENT ON TABLE ${catalog_schema}.furniture_assets IS
    '家具资产尺寸表：唯一真源 ishome-contracts registries/furniture_assets.json（24 行常规档位种子）；'
    '消费方＝布局求解 plan-layout-solve，**不是** render3d（渲染拿的是求解产出，尺寸已定死）';
COMMENT ON COLUMN ${catalog_schema}.furniture_assets.width_mm IS
    '宽，点值，**整数毫米**（量纲入名，《开发规范》§4.1 全链路毫米，本表无例外——'
    '用户裁决 2026-09-07"量纲统一毫米，家具资产表不例外"）';
COMMENT ON COLUMN ${catalog_schema}.furniture_assets.category_is_retired IS
    '恒 false 的生成列，只为让复合外键指到在役品类：**退役品类零资产行由结构堵死**，'
    '不靠要人记得过滤的标记位；反向也堵死——还有资产行的品类退不了役，撤行必须先于退役';
COMMENT ON COLUMN ${catalog_schema}.furniture_assets.sku_ref IS
    '型号引用，可空。**今天 24 行全空**——没有一行对应真货；接真实家具数据那天填';
COMMENT ON COLUMN ${catalog_schema}.furniture_assets.size_source IS
    '尺寸来源标（闭集）：regular-tier=常规住宅档位，不是真实家具、不是这户的实测/选定值。'
    '照 render3d models.py heights_source 的先例办——mock 与真值要在产出上分得开：'
    '一版布局是按真实家具算的还是按常规档位算的，看图的人有权知道';
COMMENT ON COLUMN ${catalog_schema}.furniture_assets.provenance IS
    '这一行的数出自哪儿（逐行出处）：抄依据注释 5 行 / 无定源 7 行 / 拟真填充无真跑来路 12 行。'
    '与 size_source 是两栏、不能互相代替：size_source 答"这批数是哪一类来源"（24 行全同），'
    '本列答"这一行的数出自哪儿"（分三种）；折进 size_source 就会把 19 行没有定源这件事盖掉。'
    '来路里逐字引用的 docstring 保留米制原文（"1.8×2.0m 是国标双人床…"）——引用不改写';
