package com.ishome.project.domain.rulebook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * lkp- 求值（纯函数，无 IO）：同输入同输出（规则 8.2 可重放）。
 *
 * <p>三条求值路径：①参数带 value → 直取（formula 仅为推导说明）；②仅带 formula → 按 assetId 显式实现
 * 代入匿名输入——公式的可执行形态在此登记，未登记/输入缺失 → gap-；③无值无公式 → gap-。 结果按 lkpId 排序，数值全为整数毫米/原样单位——不引入浮点位数漂移。
 */
public final class RulebookEvaluator {

  /** 直径±区间公式的毫米余量（主厨身高/2 + [50,100]，规范 §5.2 定制尺寸族）。 */
  private static final int COUNTER_OFFSET_MIN = 50;

  private static final int COUNTER_OFFSET_MAX = 100;

  public ReportDataPackage evaluate(List<ReleaseSnapshot> snapshots, EvaluationInput input) {
    List<ReleaseSnapshot> ordered =
        snapshots.stream().sorted(Comparator.comparing(ReleaseSnapshot::domain)).toList();
    List<ReportAnchor> anchors = new ArrayList<>();
    List<GapRecord> gaps = new ArrayList<>();
    Map<String, List<PersonaAssetRef>> personas = new TreeMap<>();
    for (ReleaseSnapshot snapshot : ordered) {
      personas.put(snapshot.domain(), List.copyOf(snapshot.personas()));
      for (ParameterAsset parameter : snapshot.parameters()) {
        resolve(parameter, snapshot.releaseTag(), input, anchors, gaps);
      }
    }
    anchors.sort(Comparator.comparing(ReportAnchor::lkpId));
    gaps.sort(Comparator.comparing(GapRecord::lkpId));
    return new ReportDataPackage(
        ordered.stream().map(ReleaseSnapshot::domain).toList(),
        ordered.stream().map(ReleaseSnapshot::ref).toList(),
        List.copyOf(anchors),
        List.copyOf(gaps),
        personas,
        input);
  }

  private void resolve(
      ParameterAsset parameter,
      String releaseTag,
      EvaluationInput input,
      List<ReportAnchor> anchors,
      List<GapRecord> gaps) {
    if (parameter.value() != null && !parameter.value().isEmpty()) {
      anchors.add(anchor(parameter, releaseTag, parameter.value()));
      return;
    }
    if (parameter.formula() == null || parameter.formula().isBlank()) {
      gaps.add(new GapRecord(parameter.assetId(), "empty_definition", "参数无值无公式"));
      return;
    }
    Map<String, Object> computed =
        switch (parameter.assetId()) {
          case "lkp-counter-height" ->
              input.chiefHeightMm() == null
                  ? null
                  : range(
                      input.chiefHeightMm() / 2 + COUNTER_OFFSET_MIN,
                      input.chiefHeightMm() / 2 + COUNTER_OFFSET_MAX);
          case "lkp-wardrobe-rod" ->
              input.tallestHeightMm() == null
                  ? null
                  : point(Math.round(input.tallestHeightMm() * 1.2f));
          case "lkp-mirror-height" ->
              input.eyeHeightMm() == null ? null : point(input.eyeHeightMm());
          case "lkp-tv-distance" ->
              input.tvScreenHeightMm() == null
                  ? null
                  : range(input.tvScreenHeightMm() * 3, input.tvScreenHeightMm() * 4);
          default -> null;
        };
    if (computed == null) {
      boolean implemented =
          switch (parameter.assetId()) {
            case "lkp-counter-height", "lkp-wardrobe-rod", "lkp-mirror-height", "lkp-tv-distance" ->
                true;
            default -> false;
          };
      gaps.add(
          new GapRecord(
              parameter.assetId(),
              implemented ? "missing_input" : "formula_not_implemented",
              parameter.formula()));
      return;
    }
    anchors.add(anchor(parameter, releaseTag, computed));
  }

  private ReportAnchor anchor(
      ParameterAsset parameter, String releaseTag, Map<String, Object> value) {
    return new ReportAnchor(
        parameter.assetId(),
        parameter.name(),
        parameter.numberClass(),
        parameter.unit(),
        value,
        releaseTag,
        parameter.source(),
        parameter.calibration(),
        !"calibrated".equals(parameter.calibration()));
  }

  private static Map<String, Object> range(int min, int max) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("min", min);
    value.put("max", max);
    return value;
  }

  private static Map<String, Object> point(long v) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("v", v);
    return value;
  }
}
