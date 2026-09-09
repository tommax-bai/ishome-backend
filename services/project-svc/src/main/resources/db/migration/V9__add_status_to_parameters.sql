-- 参数条目的入册状态落库（用户裁决 2026-09-09「占比只由算得」：三条搜来的占比临时锚退役、原值留档）。
--
-- 立案：budget 域 lkp-budget-share / lkp-budget-tier-gap / lkp-budget-driver 是 8-27 起从公开行情文章搜来的
-- "大家装修时的占比"，裁决改为占比＝该项金额 ÷ 总价、金额＝单价 × 这家的量，搜来的数不再下发。退役不是删行：
-- release 不可变、回滚＝切回旧 release_tag（规则 4.12），旧值要留在表里作对照；而 parameters 表此前没有
-- 状态列——check 形态的 status（V4，observing|active|retired）是唯一先例，本列照它的取法：种子声明、
-- 灌库照写、快照照实带上、**求值线对 retired 条目不产落点也不记 gap-**（它不是求不出，是已裁定不给）。
--
-- 只有两值：参数没有"观察态"——观察态是判官判据的入册门禁（规则 4.17），参数走的是 calibration 状态机。
-- 缺省 active：V9 之前的所有行与 release 快照没有这一列，读作 active，行为与发布时一致。
ALTER TABLE ${rulebook_schema}.parameters
    ADD COLUMN status varchar(16) NOT NULL DEFAULT 'active'
        CONSTRAINT ck_parameters_status CHECK (status IN ('active', 'retired'));

COMMENT ON COLUMN ${rulebook_schema}.parameters.status IS
    '入册状态 active|retired（2026-09-09）：retired=已裁定不再下发、原值留档；求值线不产落点不记 gap-';
