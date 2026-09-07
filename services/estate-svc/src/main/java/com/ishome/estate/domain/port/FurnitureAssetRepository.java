package com.ishome.estate.domain.port;

import com.ishome.estate.domain.catalog.FurnitureAsset;
import com.ishome.estate.domain.catalog.SizeTier;
import java.util.List;
import java.util.Optional;

/**
 * 家具资产尺寸表读取 port——**只读**。
 *
 * <p>写路径今天不在本服务：25 行常规档位种子由 {@code scripts/catalog/import_furniture_assets.py} 从契约仓灌入 （契约改了重跑即重灌，同
 * rulebook 种子的既有做法）；真实家具数据进来后由 catalog 域的目录进口那一侧写， **仍写同一张表**（用户 2026-09-07 已拍：表结构不变，只改值与
 * size_source）。
 *
 * <p>取不到时的降级由调用侧（布局求解）执行、且要在求解自证里看得见：该品类有 standard 但没有请求的那一档 → 退到 standard，退了要看得见；该品类一行都没有 → 那条需求进
 * {@code unplaced[]} 逐件报出，不硬塞。 本 port 只负责如实答"有没有、是多少"，不代替调用侧做降级。
 */
public interface FurnitureAssetRepository {

  /** 按品类取全部候选档位，按宽度升序（同输入同输出）；品类无行时返回空列表，不抛。 */
  List<FurnitureAsset> listByCategory(String category);

  /** 按品类 + 档取那一行（{@code (category, size_tier)} 唯一）；取不到即空，降级由调用侧决定。 */
  Optional<FurnitureAsset> findByCategoryAndSizeTier(String category, SizeTier sizeTier);
}
