package com.ishome.estate.infrastructure.persistence;

/**
 * {@code svc_catalog.furniture_assets} 持久化对象。
 *
 * <p>{@code sizeTier} 以列上的原始字符串读出（DB 存 {@code small/standard/large} 小写， 与 Java 枚举常量名不同形），转枚举在
 * RepositoryImpl（{@link com.ishome.estate.domain.catalog.SizeTier#fromWireValue}）。
 */
public class FurnitureAssetPO {

  private String assetId;
  private String category;
  private String sizeTier;
  private int widthMm;
  private int depthMm;
  private int heightMm;
  private String skuRef;
  private String sizeSource;
  private String provenance;

  public String getAssetId() {
    return assetId;
  }

  public void setAssetId(String assetId) {
    this.assetId = assetId;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getSizeTier() {
    return sizeTier;
  }

  public void setSizeTier(String sizeTier) {
    this.sizeTier = sizeTier;
  }

  public int getWidthMm() {
    return widthMm;
  }

  public void setWidthMm(int widthMm) {
    this.widthMm = widthMm;
  }

  public int getDepthMm() {
    return depthMm;
  }

  public void setDepthMm(int depthMm) {
    this.depthMm = depthMm;
  }

  public int getHeightMm() {
    return heightMm;
  }

  public void setHeightMm(int heightMm) {
    this.heightMm = heightMm;
  }

  public String getSkuRef() {
    return skuRef;
  }

  public void setSkuRef(String skuRef) {
    this.skuRef = skuRef;
  }

  public String getSizeSource() {
    return sizeSource;
  }

  public void setSizeSource(String sizeSource) {
    this.sizeSource = sizeSource;
  }

  public String getProvenance() {
    return provenance;
  }

  public void setProvenance(String provenance) {
    this.provenance = provenance;
  }
}
