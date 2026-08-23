package com.ishome.project.application;

import com.ishome.project.domain.CognitiveState;

/** 业务事实 slot_filled——chat 抽取后的结构化槽位值（本服务不理解自然语言，只收结构化事实）。 sourceEventId 为来源事件引用（审计与幂等锚点）。 */
public record SlotFilledCommand(
    String projectId,
    String slotKey,
    String value,
    CognitiveState cognitiveState,
    String sourceEventId,
    double confidence) {}
