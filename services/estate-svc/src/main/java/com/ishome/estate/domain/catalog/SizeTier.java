package com.ishome.estate.domain.catalog;

/**
 * 家具档位三值，逐字对上布局求解契约草案 {@code requirements[].size_pref} 的三档枚举。
 *
 * <p>常量名 UPPER_SNAKE（规范 §2.1），但 DB 与契约里存的是小写 {@code small/standard/large}， 故显式声明线上值而不用 {@link
 * #name()}——同 §2.4 activity 注册名的做法（{@code @activity.defn(name="floorplan-parse")}， 注册名显式声明、不等于函数名）。
 *
 * <p>为什么不把 DB 值改成 UPPER_SNAKE 以对齐规范 §2.1 的"逐字一致"：这三个值是**跨仓协议**—— 求解侧（Python）按契约逐字读 {@code
 * size_pref}，改大小写等于改线上协议。冲突取契约那一侧， 映射收口在本枚举一处，别处不得再写这三个字面量。
 */
public enum SizeTier {
  SMALL("small"),
  STANDARD("standard"),
  LARGE("large");

  private final String wireValue;

  SizeTier(String wireValue) {
    this.wireValue = wireValue;
  }

  /** 契约与 DB 存的那个字符串（{@code svc_catalog.furniture_assets.size_tier} 逐字一致）。 */
  public String wireValue() {
    return wireValue;
  }

  /** 由契约/DB 的字符串取枚举；表外值直接抛——闭集之外的档位不该走到这一步（列上有 CHECK 兜底）。 */
  public static SizeTier fromWireValue(String wireValue) {
    for (SizeTier tier : values()) {
      if (tier.wireValue.equals(wireValue)) {
        return tier;
      }
    }
    throw new IllegalArgumentException("档位不在闭集内：" + wireValue);
  }
}
