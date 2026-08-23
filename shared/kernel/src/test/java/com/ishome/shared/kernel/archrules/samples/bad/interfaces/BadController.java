package com.ishome.shared.kernel.archrules.samples.bad.interfaces;

import com.ishome.shared.kernel.archrules.samples.bad.infrastructure.BadRepositoryImpl;

/**
 * 规则自检样例（违规方）：controller 直调仓储实现——同时违反规则①（interfaces 依赖 infrastructure）与规则③（controller 直调
 * Repository），必须被规则集拦下。
 */
public final class BadController {

  private final BadRepositoryImpl badRepository = new BadRepositoryImpl();

  public String load(String id) {
    return badRepository.findRecord(id);
  }
}
