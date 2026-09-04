package com.ishome.project.application;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 编排侧产物词表 → 本服务 artifact_type 的映射表（数据）。
 *
 * <p>两套词表各归各的仓：contracts genpipe.v1 {@code floorplan_visuals_product} 是编排侧的， artifact_type
 * 是本服务流程定义里的。 认不得的产物**照原词登记、不呈现**（记着比丢了强，且词表只增不改——新词出现时老服务不该整批拒收）。
 * 三张图是"该送到业主手里的"；母版、几何、特征解析是血缘与报告线的原料，登记但不呈现。
 */
public final class VisualsProductCatalog {
  public static final String ARTIFACT_VISION_MOOD_IMAGE = "vision_mood_image";
  public static final String ARTIFACT_VISION_BRIEF_IMAGE = "vision_brief_image";
  public static final String ARTIFACT_VISION_STYLE_IMAGE = "vision_style_image";
  public static final String ARTIFACT_PLAN_MASTER = "plan_master";
  public static final String ARTIFACT_FLOORPLAN_GEOMETRY = "floorplan_geometry";
  public static final String ARTIFACT_FLOORPLAN_READING = "floorplan_reading";

  private static final Map<String, String> ARTIFACT_TYPE_BY_PRODUCT =
      Map.of(
          "mood_image", ARTIFACT_VISION_MOOD_IMAGE,
          "brief_image", ARTIFACT_VISION_BRIEF_IMAGE,
          "style_image", ARTIFACT_VISION_STYLE_IMAGE,
          "plan_master", ARTIFACT_PLAN_MASTER,
          "floorplan_geometry", ARTIFACT_FLOORPLAN_GEOMETRY,
          "floorplan_reading", ARTIFACT_FLOORPLAN_READING);

  /** 送到业主手里的那几类，按呈现顺序：情绪图 → 功能说明图 → 风格图（视觉提案 §13 三张同底稿的既定顺序）。 */
  public static final Set<String> DELIVERABLE_ARTIFACT_TYPES =
      Set.of(ARTIFACT_VISION_MOOD_IMAGE, ARTIFACT_VISION_BRIEF_IMAGE, ARTIFACT_VISION_STYLE_IMAGE);

  private static final Map<String, Integer> PRESENTATION_ORDER =
      Map.of(
          ARTIFACT_VISION_MOOD_IMAGE, 0,
          ARTIFACT_VISION_BRIEF_IMAGE, 1,
          ARTIFACT_VISION_STYLE_IMAGE, 2);

  private VisualsProductCatalog() {}

  public static Optional<String> artifactTypeOf(String product) {
    return Optional.ofNullable(ARTIFACT_TYPE_BY_PRODUCT.get(product));
  }

  public static boolean isDeliverable(String artifactType) {
    return DELIVERABLE_ARTIFACT_TYPES.contains(artifactType);
  }

  public static int presentationOrder(String artifactType) {
    return PRESENTATION_ORDER.getOrDefault(artifactType, Integer.MAX_VALUE);
  }
}
