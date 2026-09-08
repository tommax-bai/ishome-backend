-- 公式点值的取整粒度落库（规则 4.10e 增补，用户裁决 2026-09-08：取整按数据逐条声明，不声明即不取整）。
--
-- 立案：9-07 真跑册衣柜挂杆高印出 2136 mm——1780 × 1.2 的原样结果，定制商按整十毫米下料，那个 6 是夹具
-- 照出来的假精度。取整、不取整都由数据说：尺寸类 10 mm、估算长度 0.1 米，用户原值/计数/规范原值**不声明**；
-- 求值线按声明取整、区间两端各自取整、推导原文写出取整前后；渲染层一字不动（渲染层禁换算红线不变）。
-- 它不是规则 4.10e 禁的"自造精度声明"：取整声明有源（施工精度），tolerance 那种是编一个没有源的数字表达不确定。
--
-- 列约束与核验各拦一路（同 ck_parameters_value_kind 的取法）：
--   · 粒度必须为正——0 或负数没有"取整到"的含义；
--   · 只许跟着公式出现——直取值是规范原值/用户原值，给它加取整等于加一层没人裁过的精度。
-- NULL 允许且是常态：不声明即不取整；V8 之前的 release 快照没有这一列，读作 NULL，行为与发布时一致。
ALTER TABLE ${rulebook_schema}.parameters
    ADD COLUMN round_to numeric
        CONSTRAINT ck_parameters_round_to_positive CHECK (round_to IS NULL OR round_to > 0),
    ADD CONSTRAINT ck_parameters_round_to_needs_formula CHECK (round_to IS NULL OR formula IS NOT NULL);

COMMENT ON COLUMN ${rulebook_schema}.parameters.round_to IS
    '公式点值取整粒度，随 unit 计（规则 4.10e 增补，2026-09-08）：求值线按它取整、区间两端各自取整；NULL=不取整（用户原值/计数/规范原值）';
