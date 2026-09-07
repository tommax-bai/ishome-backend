package com.ishome.project.application;

import com.ishome.project.domain.Slot;
import java.util.List;

/**
 * 按属主取或建项目的结果：created=true 表示这一次新建（并已进入首个里程碑）。
 *
 * <p>{@code slots} = 这个项目上已有的全部槽位真相（新建的项目为空）。带回它是因为会话侧的会话态只活在它自己的进程里、重启即失，而槽位真相一直在
 * svc_project.slots——2026-09-06 业主给过的建筑面积，9-07 重启后又被问了一遍。会话侧判"还缺什么"之前 按属主问一次就能把已有的读回来，不必另开一个读接口（契约
 * project.v1 {@code project_summary.slots}）。
 */
public record ProjectFindOrCreateResult(
    String projectId,
    String currentMilestone,
    String processVersion,
    boolean created,
    List<Slot> slots) {}
