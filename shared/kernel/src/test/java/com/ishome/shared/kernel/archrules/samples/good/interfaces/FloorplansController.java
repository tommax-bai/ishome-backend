package com.ishome.shared.kernel.archrules.samples.good.interfaces;

import com.ishome.shared.kernel.archrules.samples.good.application.ClaimAppService;
import com.ishome.shared.kernel.archrules.samples.good.domain.Floorplan;

/** 规则自检样例：controller 只依赖 application，不直调 repository。 */
public final class FloorplansController {

  private final ClaimAppService claimAppService;

  public FloorplansController(ClaimAppService claimAppService) {
    this.claimAppService = claimAppService;
  }

  public Floorplan claim(String floorplanId) {
    return claimAppService.claimFloorplan(floorplanId);
  }
}
