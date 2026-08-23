package com.ishome.shared.kernel.archrules.samples.good.application;

import com.ishome.shared.kernel.archrules.samples.good.domain.Floorplan;
import com.ishome.shared.kernel.archrules.samples.good.domain.FloorplanRepository;

/** 规则自检样例：用例服务（application 层，事务边界落点）。 */
public final class ClaimAppService {

  private final FloorplanRepository floorplanRepository;

  public ClaimAppService(FloorplanRepository floorplanRepository) {
    this.floorplanRepository = floorplanRepository;
  }

  public Floorplan claimFloorplan(String floorplanId) {
    return floorplanRepository.getFloorplan(floorplanId);
  }
}
