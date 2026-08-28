-- 规则 4.16① 的后半句落地：交叉验证不一致的条目"挂 conflict **且不进 release**"。
-- V2 建表时只实现了前半句（source_2 记第二源），冲突标记无处安放——获取回路第三轮
-- （run-2026-08-28-2）写下 `conflict: true` 时暴露：该标记被导入链路静默丢弃，条目照常进快照。
--
-- 语义与 calibration 正交，别混：
--   draft      = 依据不足（还没被证明对）→ 仍进 release，只能降档呈现（规则 4.10）；
--   conflict   = 依据互斥（两个源各说各话）→ **不进 release**——降档也无从降起：
--                连"这个数是多少"都没有共识时，参考形态呈现的仍是一个没有共识的数。
-- 只加在有外部真源的三形态（规则 4.16 点名 attribute/parameter/rule）；template/vocabulary
-- 由自迭代回路自产、无交叉验证语义，不加。
ALTER TABLE ${rulebook_schema}.rules      ADD COLUMN conflict boolean NOT NULL DEFAULT false;
ALTER TABLE ${rulebook_schema}.parameters ADD COLUMN conflict boolean NOT NULL DEFAULT false;
ALTER TABLE ${rulebook_schema}.attributes ADD COLUMN conflict boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN ${rulebook_schema}.parameters.conflict IS
    '规则 4.16①：源间不一致，发布时排除出 release 快照；与 calibration 正交';
