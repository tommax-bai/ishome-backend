-- 两层模型落库（规范 v2.8 规则 1.9，用户裁决 2026-08-30：正文可引用落点的某一项）。
--
-- 立案材料是真跑证据，不是设想：灯光同包同码同参六轮 0/6 过检，六轮里全部 27 种"不在本域落点
-- 对象内"的占位符，27/27 逐字等于「真实落点 id」＋「该落点 value 字典里一个真实的键」——模型不是
-- 不守规矩，是**想说的那句话没有合法写法**（"沙发旁读书那块要单独加亮"只能整条引用照度落点）。
--
-- ── value_kind：一条落点由哪种项构成 ──────────────────────────────────────────────
-- 一条落点 = 若干「项」，一项的值 = 一个数，或一个区间。value_kind 同时判定三件事，**都不靠推断**：
--   ① value 的形态：single→标量 / range→{min,max} / 其余五类→项名 → 标量|{min,max}；
--   ② 可否单项引用：single/range 只有一个匿名项，只能整条引用 {lkp-x}；其余五类可写 {lkp-x.项名}；
--   ③ 项名受哪套约束：tier/dimension 闭集、comparison 形态受控、scenario/component 受控词表。
-- 列上带 CHECK 而不只靠 verify_seeds：核验拦的是**改源**这条路，列约束拦的是所有路（同
-- ck_checks_status 的取法）。NULL 允许——公式落点在可执行形态登记前不产出落点，形态待定；
-- 硬填一个假的比缺席更坏（坦白缺口，规则 4.18）。
ALTER TABLE ${rulebook_schema}.parameters
    ADD COLUMN value_kind varchar(16)
        CONSTRAINT ck_parameters_value_kind CHECK (value_kind IS NULL OR value_kind IN
            ('single', 'range', 'scenario', 'tier', 'dimension', 'component', 'comparison'));

-- ── reference_plane：元信息出 value ──────────────────────────────────────────────
-- 参考平面（"0.75m 水平面"、"化妆台 台面（混合照明）"）此前挤在 value 里当一个键，与真正的项同层。
-- 只要元信息与项同层，`{lkp-x.plane}`（引用出一个平面字符串）就是语法上合法的写法，靠"别那么写"
-- 约束不住——所以给它自己的列（规则 1.9 二）。单位早有 unit 列，本次只把 value 里那份重复删掉。
ALTER TABLE ${rulebook_schema}.parameters
    ADD COLUMN reference_plane text;

COMMENT ON COLUMN ${rulebook_schema}.parameters.value_kind IS
    '值的构成类别（规则 1.9，v2.8）：single|range|scenario|tier|dimension|component|comparison；'
    '判定 value 形态、可否单项引用、项名受哪套约束——三者都不靠推断。NULL=公式落点形态待定';
COMMENT ON COLUMN ${rulebook_schema}.parameters.reference_plane IS
    '参考平面及其高度（国标术语）：v2.8 前它挤在 value 里与项同层，元信息出 value 是规则 1.9 二的要求';
