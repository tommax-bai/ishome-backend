-- 判官反例库落地（图 v0.2 §3 出口过检·判官层 + 规则 4.17 自迭代回路）。
-- 用户裁决 2026-08-28：反例库**不用旧材料**（原"13 轮附录 A 转化"作废），改由冷启动期真跑
-- 问题样本自建——规则 4.17 原文即"种子集初版来自冷启动期问题样本"，且真跑样本的真实度高于
-- 旧文档转写；规范 v2.3 §12 同步把"以想象填充判官反例库"列为禁止项。
--
-- ── 为什么样例挂在 checks 表，而不是新建第三张"判官反例"表（规则 4.10b 归谬）────────────
-- 反例样例是**用样例定义的纪律**：它陈述的不是关于世界的事实（没有对错可核、没有外部源可挂、
-- 没有信号可等），而是系统对自身输出的约束。规则 4.10b 已把这类东西的存在形式钉死为 check：
-- 不进 calibration 状态机，正当性锚是 decided_by（裁决记录）。既然如此，样例就该挂在它所例示的
-- 那条 cr- 判据之下——另起一张表等于承认存在"第三类"（既非知识断言、也非 check），
-- 而 4.10b 明写"不存在第三类，不设纪律型知识条目"。
-- 与 4.10b"check 不得携带内容数值"不冲突：样例是文本（原句 / 为什么错 / 怎么改），不是阈值。
-- 数值阈值仍只能经 threshold_refs 引用 lkp- 参数，借样例夹带一个数字在结构上仍然没有位置。
ALTER TABLE ${rulebook_schema}.checks
    ADD COLUMN examples jsonb NOT NULL DEFAULT '[]'::jsonb;

-- status = 规则 4.17 入册门禁第二道（观察态）的**数据侧开关**：
--   observing  判官命中只记录不拦截——首批一律此档：判官与写手同源，未经观察期不得有拦截权；
--   active     命中即违规，进重写循环（转正只能由观察期数据授予，同 calibrated 只能由核验授予）；
--   retired    停用留档——判据可回滚，不删行（release 不可变，回滚=切回旧 release_tag）。
-- 拦截与否由**数据**决定，不由代码分支决定：转正走发版，代码里没有"要不要拦"的开关。
-- 默认 observing = 一条判据进到世界上时不带拦截权（门禁失效方向必须是少拦不是多拦）。
ALTER TABLE ${rulebook_schema}.checks
    ADD COLUMN status varchar(16) NOT NULL DEFAULT 'observing'
        CONSTRAINT ck_checks_status CHECK (status IN ('observing', 'active', 'retired'));

-- 已在跑的老条目按真相回填：V2/V3 灌进来的都是**规则层确定性机检**（regex_deny/count_max…），
-- 此刻就在 reportgen gate 里拦截。给它们标 observing 等于让表撒谎（状态真相在表，规则 8.1 同源
-- 纪律）。观察态是"判官新学来的判据"的入册门槛，不是"已编译好的确定性纪律"的降级。
UPDATE ${rulebook_schema}.checks SET status = 'active';

COMMENT ON COLUMN ${rulebook_schema}.checks.examples IS
    '判官反例样例 [{bad,why,fixed}]：只收真跑观察到的样本，禁想象填充（规则 4.17，规范 v2.3 §12）';
COMMENT ON COLUMN ${rulebook_schema}.checks.status IS
    '入册状态 observing|active|retired：规则 4.17 门禁二（观察态），判官层据此决定记录还是拦截';

-- check_type 新增取值 semantic_judge（列无约束，不需要改列定义，在此登记口径）：
-- 语义判据，规则层**判不出**（"这句话算不算编造事实"没有确定性判据，规则 4.10c 已写明机检不假
-- 实现），只由判官层按 examples 执行。规则层碰到它一律跳过——无 pattern 即不执行，行为不变。
