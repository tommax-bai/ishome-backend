package com.ishome.project.domain.definition;

import com.ishome.project.domain.CognitiveState;
import java.util.Set;

/**
 * 简单谓词：指定槽位已填，且认知状态落在可接受集合内。
 *
 * @param slotKey 槽位 key
 * @param acceptedStates 可接受的认知状态集合；空集 = 已填即可（任一状态）
 */
public record SlotCriterion(String slotKey, Set<CognitiveState> acceptedStates) {

  public static SlotCriterion filled(String slotKey) {
    return new SlotCriterion(slotKey, Set.of());
  }

  public static SlotCriterion userConfirmed(String slotKey) {
    return new SlotCriterion(slotKey, Set.of(CognitiveState.USER_CONFIRMED));
  }
}
