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
 *
 * <p>求出值之后还要过一道**降档纪律**（{@link AnchorPresentationPolicy}，规则 4.10）：按本次求值服务的产物 权益档判呈现档位；判为 {@link
 * AnchorPresentation#WITHHELD} 的落点**不进 anchors**，只在 withheldAnchors 留 id
 * 与原因。门禁执行在这里而不是靠成文线自觉——未背书的值根本不下发才叫强制。
 */
public final class RulebookEvaluator {

  /** 直径±区间公式的毫米余量（主厨身高/2 + [50,100]，规范 §5.2 定制尺寸族）。 */
  private static final int COUNTER_OFFSET_MIN = 50;

  private static final int COUNTER_OFFSET_MAX = 100;

  private final AnchorPresentationPolicy presentationPolicy = new AnchorPresentationPolicy();

  public ReportDataPackage evaluate(
      List<ReleaseSnapshot> snapshots, EvaluationInput input, ArtifactEntitlement entitlement) {
    List<ReleaseSnapshot> ordered =
        snapshots.stream().sorted(Comparator.comparing(ReleaseSnapshot::domain)).toList();
    List<ReportAnchor> anchors = new ArrayList<>();
    List<WithheldAnchor> withheld = new ArrayList<>();
    List<GapRecord> gaps = new ArrayList<>();
    Map<String, List<PersonaAsset>> personas = new TreeMap<>();
    Map<String, List<CheckAsset>> checks = new TreeMap<>();
    Map<String, List<String>> bannedTerms = new TreeMap<>();
    for (ReleaseSnapshot snapshot : ordered) {
      personas.put(
          snapshot.domain(),
          snapshot.personas().stream()
              .sorted(Comparator.comparing(PersonaAsset::assetId))
              .toList());
      checks.put(
          snapshot.domain(),
          snapshot.checks().stream().sorted(Comparator.comparing(CheckAsset::assetId)).toList());
      bannedTerms.put(snapshot.domain(), snapshot.bannedTerms().stream().sorted().toList());
      for (ParameterAsset parameter : snapshot.parameters()) {
        resolve(parameter, snapshot.releaseTag(), input, entitlement, anchors, withheld, gaps);
      }
    }
    anchors.sort(Comparator.comparing(ReportAnchor::lkpId));
    withheld.sort(Comparator.comparing(WithheldAnchor::lkpId));
    gaps.sort(Comparator.comparing(GapRecord::lkpId));
    return new ReportDataPackage(
        entitlement,
        ordered.stream().map(ReleaseSnapshot::domain).toList(),
        ordered.stream().map(ReleaseSnapshot::ref).toList(),
        List.copyOf(anchors),
        List.copyOf(withheld),
        List.copyOf(gaps),
        personas,
        checks,
        bannedTerms,
        input);
  }

  private void resolve(
      ParameterAsset parameter,
      String releaseTag,
      EvaluationInput input,
      ArtifactEntitlement entitlement,
      List<ReportAnchor> anchors,
      List<WithheldAnchor> withheld,
      List<GapRecord> gaps) {
    if (parameter.value() != null && !parameter.value().isEmpty()) {
      publish(parameter, releaseTag, parameter.value(), entitlement, anchors, withheld);
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
    publish(parameter, releaseTag, computed, entitlement, anchors, withheld);
  }

  /** 求值成功后的下发决定：过降档纪律，隐藏档只留审计条，其余进落点对象（规则 4.10）。 */
  private void publish(
      ParameterAsset parameter,
      String releaseTag,
      Map<String, Object> value,
      ArtifactEntitlement entitlement,
      List<ReportAnchor> anchors,
      List<WithheldAnchor> withheld) {
    AnchorPresentationPolicy.Verdict verdict =
        presentationPolicy.decide(
            parameter.calibration(), parameter.numberClass(), value, entitlement);
    if (verdict.presentation() == AnchorPresentation.WITHHELD) {
      withheld.add(new WithheldAnchor(parameter.assetId(), releaseTag, verdict.withholdReason()));
      return;
    }
    anchors.add(anchor(parameter, releaseTag, value, verdict.presentation()));
  }

  private ReportAnchor anchor(
      ParameterAsset parameter,
      String releaseTag,
      Map<String, Object> value,
      AnchorPresentation presentation) {
    return new ReportAnchor(
        parameter.assetId(),
        parameter.name(),
        parameter.numberClass(),
        parameter.unit(),
        value,
        releaseTag,
        parameter.source(),
        parameter.calibration(),
        !"calibrated".equals(parameter.calibration()),
        presentation);
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
