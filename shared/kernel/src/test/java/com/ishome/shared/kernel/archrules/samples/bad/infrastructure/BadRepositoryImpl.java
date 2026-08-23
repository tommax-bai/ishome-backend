package com.ishome.shared.kernel.archrules.samples.bad.infrastructure;

/** 规则自检样例（违规方）：将被 controller 直调的仓储实现。 */
public final class BadRepositoryImpl {

  public String findRecord(String id) {
    return id;
  }
}
