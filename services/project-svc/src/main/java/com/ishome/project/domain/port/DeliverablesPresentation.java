package com.ishome.project.domain.port;

import com.ishome.project.domain.GenerationFailure;
import com.ishome.project.domain.ProjectOwner;
import java.util.List;

/**
 * 一次呈现：送给哪个属主、哪些产物；{@code failure} 非空＝这一次是"没做出来"的告知，{@code deliverables} 为空。 {@code deliveryId}
 * 由本服务铸（ULID），会话侧用它派生出站幂等键。
 */
public record DeliverablesPresentation(
    String deliveryId,
    String projectId,
    ProjectOwner owner,
    List<PresentedDeliverable> deliverables,
    GenerationFailure failure,
    String taskType) {}
