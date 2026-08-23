package com.ishome.shared.kernel.archrules.samples.good.infrastructure;

import com.ishome.shared.kernel.archrules.samples.good.domain.Floorplan;
import com.ishome.shared.kernel.archrules.samples.good.domain.FloorplanRepository;

/** 规则自检样例：仓储实现在 infrastructure 层，实现 domain 的接口。 */
public final class FloorplanRepositoryImpl implements FloorplanRepository {

  @Override
  public Floorplan getFloorplan(String floorplanId) {
    return new Floorplan(floorplanId);
  }
}
