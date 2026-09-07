package com.ishome.estate.infrastructure.persistence;

import com.ishome.estate.domain.catalog.FurnitureAsset;
import com.ishome.estate.domain.catalog.SizeTier;
import com.ishome.estate.domain.port.FurnitureAssetRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@code svc_catalog.furniture_assets} PG 实现：只读，行 id（ULID）是 infrastructure 细节、不出这一层， 对外的资产标识是
 * {@code asset_id}（语义命名）。
 */
@Repository
public class FurnitureAssetRepositoryImpl implements FurnitureAssetRepository {

  private final FurnitureAssetMapper furnitureAssetMapper;

  public FurnitureAssetRepositoryImpl(FurnitureAssetMapper furnitureAssetMapper) {
    this.furnitureAssetMapper = furnitureAssetMapper;
  }

  @Override
  public List<FurnitureAsset> listByCategory(String category) {
    return furnitureAssetMapper.listActiveByCategory(category).stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public Optional<FurnitureAsset> findByCategoryAndSizeTier(String category, SizeTier sizeTier) {
    return Optional.ofNullable(
            furnitureAssetMapper.findActiveByCategoryAndSizeTier(category, sizeTier.wireValue()))
        .map(this::toDomain);
  }

  private FurnitureAsset toDomain(FurnitureAssetPO po) {
    return new FurnitureAsset(
        po.getAssetId(),
        po.getCategory(),
        SizeTier.fromWireValue(po.getSizeTier()),
        po.getWidthM(),
        po.getDepthM(),
        po.getHeightM(),
        po.getSkuRef(),
        po.getSizeSource(),
        po.getProvenance());
  }
}
