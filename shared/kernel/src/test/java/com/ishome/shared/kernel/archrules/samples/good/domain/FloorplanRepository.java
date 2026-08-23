package com.ishome.shared.kernel.archrules.samples.good.domain;

/** 规则自检样例：repository 接口（port）在 domain 层，实现在 infrastructure。 */
public interface FloorplanRepository {

  /** get = 必得，取不到抛异常（规范 §三 查询命名）。 */
  Floorplan getFloorplan(String floorplanId);
}
