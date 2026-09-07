package com.ishome.estate.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * {@code svc_catalog.furniture_assets} Mapper——**只读**（本服务今天不写这张表，写路径见 {@link
 * com.ishome.estate.domain.port.FurnitureAssetRepository} 的说明）。
 *
 * <p>schema 名经 MyBatis 配置变量 {@code ${catalogSchema}} 注入（application.yml 正式=svc_catalog；
 * 集成测试=svc_catalog_it）——与 Flyway placeholder {@code catalog_schema} 同一事实两处注入， 同 project-svc {@code
 * ReleaseMapper} 的取法。
 *
 * <p>排序按 {@code width_m} 升序 + {@code asset_id} 兜底：候选顺序必须确定（同输入同输出）。 不按 {@code size_tier} 字面排——字母序是
 * large/small/standard，与档位大小无关；真数据里 宽度升序恰好就是 small→standard→large，用它即可，不必在 SQL 里写一段档位映射。
 */
@Mapper
public interface FurnitureAssetMapper {

  String COLUMNS =
      "asset_id, category, size_tier, width_m, depth_m, height_m, sku_ref, size_source, provenance";

  @Select(
      "SELECT "
          + COLUMNS
          + " FROM ${catalogSchema}.furniture_assets "
          + "WHERE category = #{category} AND deleted_at IS NULL "
          + "ORDER BY width_m, asset_id")
  List<FurnitureAssetPO> listActiveByCategory(@Param("category") String category);

  @Select(
      "SELECT "
          + COLUMNS
          + " FROM ${catalogSchema}.furniture_assets "
          + "WHERE category = #{category} AND size_tier = #{sizeTier} AND deleted_at IS NULL")
  FurnitureAssetPO findActiveByCategoryAndSizeTier(
      @Param("category") String category, @Param("sizeTier") String sizeTier);
}
