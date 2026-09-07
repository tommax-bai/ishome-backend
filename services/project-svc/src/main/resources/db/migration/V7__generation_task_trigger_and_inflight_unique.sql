-- 2026-09-07 补派：该出的图没出来、业主重发一张户型图就再来一次（用户裁决："好，重发一张图的话，就再来一次。"）。
-- 派发的幂等原先是**隐式**的——靠"里程碑只迁一次"这个结构性事实兜着，本表除主键外一个约束都没有；
-- 补派把"不迁移也能铸任务"这条路放开之后那层保护就没了，必须在库里显式补回来。

-- 触发本任务的渠道事件 id（slot_filled 的 source_event_id，与 slots.source_event_id 同型同宽）。
-- 老数据为 NULL；判据"同一条渠道消息不铸第二个任务"以本列为载体。
ALTER TABLE generation_tasks ADD COLUMN trigger_event_id varchar(128);

-- 同一项目同一类任务至多一个在途：兜住并发补派（业主连发两张、中继与补派撞在一起）。
-- 只管在途（PENDING/RUNNING）——FAILED 与 COMPLETED 是历史：重跑铸新号、旧的原样留着
-- （用户裁决 2026-09-06《几何重跑当作新的一轮》），所以历史行不进射程、迁移不会被存量数据卡住。
CREATE UNIQUE INDEX uk_generation_tasks_inflight
    ON generation_tasks (project_id, task_type)
    WHERE status IN ('PENDING', 'RUNNING') AND deleted_at IS NULL;
