-- 2026-09-04 串联：项目带会话属主三元组（找回项目、送回产物都靠它）；生成任务留编排侧回流的结论原文。
-- 渠道类型只作数据值存小写标识（feishu / mock …），本服务不按它分支（规范 §6.2 白名单③）。
ALTER TABLE projects
    ADD COLUMN owner_channel_type      varchar(32),
    ADD COLUMN owner_channel_instance  varchar(128),
    ADD COLUMN owner_external_user_id  varchar(128);

-- 同一属主至多一个进行中的项目（多项目管理后置；identity 归一后键改 user_id）。
CREATE UNIQUE INDEX uk_projects_owner_active
    ON projects (owner_channel_type, owner_channel_instance, owner_external_user_id)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

-- 回流结论原文（project.v1 generation_task_result），审计与排错用；产物真相仍在 artifacts 表。
ALTER TABLE generation_tasks ADD COLUMN result jsonb;
