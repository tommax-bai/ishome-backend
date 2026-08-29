package com.ishome.project.domain.rulebook;

import java.time.LocalDate;
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
 * <p>求出值之后过两道判定，都在生产侧做完、结果随包下发（成文线只执行不重判）：①**语域**（{@link AnchorPresentationPolicy}，规则
 * 4.10a/5.8）——过没过可核性门决定能不能作判断句支点；②**标注**（{@link AnchorProvenancePolicy}，规则 4.10c，v2.4
 * 新增）——未过门或已过期的落点随带 {@link AnchorProvenance}， 成文线据此在同页挂依据标注，不标即违规。
 */
public final class RulebookEvaluator {

  /** 直径±区间公式的毫米余量（主厨身高/2 + [50,100]，规范 §5.2 定制尺寸族）。 */
  private static final int COUNTER_OFFSET_MIN = 50;

  private static final int COUNTER_OFFSET_MAX = 100;

  /** 定位数字（规则 2.3 数字三分法）：未过门时进正文要随页挂现场复核话术，见 {@link #derivedLockedTexts}。 */
  private static final String NUMBER_CLASS_LOCATING = "locating";

  /**
   * 现场复核话术的锁定文案 ID（contracts {@code registries/locked_texts.md}，正文在渲染层按 ID 取）。
   *
   * <p>常量而非配置：它是规则 4.10c 原文点名的配套话术（"安全级话术（§7 锁定文案 GUIDE_SITE_CHECK/DISCLAIM_P1）
   * 本就是为这个场景准备的"），属**纪律**不属内容——纪律的形态是机检 check 与锁定文案 ID（规则 4.10b）， 不进知识库。派生依据是落点的结构化属性，不是从 check
   * 的自然语言 requirement 里抠 ID（§12 禁止项）。
   */
  private static final String LOCKED_TEXT_SITE_CHECK = "GUIDE_SITE_CHECK";

  private final AnchorPresentationPolicy presentationPolicy = new AnchorPresentationPolicy();

  private final AnchorProvenancePolicy provenancePolicy = new AnchorProvenancePolicy();

  public ReportDataPackage evaluate(
      List<ReleaseSnapshot> snapshots,
      EvaluationInput input,
      ArtifactEntitlement entitlement,
      LocalDate evaluatedOn) {
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
        resolve(
            parameter,
            snapshot.releaseTag(),
            input,
            entitlement,
            evaluatedOn,
            anchors,
            withheld,
            gaps);
      }
    }
    anchors.sort(Comparator.comparing(ReportAnchor::lkpId));
    withheld.sort(Comparator.comparing(WithheldAnchor::lkpId));
    gaps.sort(Comparator.comparing(GapRecord::lkpId));
    return new ReportDataPackage(
        evaluatedOn,
        entitlement,
        ordered.stream().map(ReleaseSnapshot::domain).toList(),
        ordered.stream().map(ReleaseSnapshot::ref).toList(),
        List.copyOf(anchors),
        List.copyOf(withheld),
        List.copyOf(gaps),
        personas,
        checks,
        bannedTerms,
        derivedLockedTexts(anchors),
        input);
  }

  /**
   * 求值结果派生的必挂锁定文案（规则 4.10c 配套现场复核话术，v2.4 新增）。
   *
   * <p>v2.4 之前，未过门的定位数字一律隐藏——"参考口吻的定位数字"被认为不存在。裁决取消隐藏后它照常进正文，
   * 风险改由两件事共同承接：**同页依据标注**（业主知道这个数有多硬）＋**现场复核话术**（业主知道拿它去施工前要复核）。 缺后者，标注就只剩一句"这条没依据"，没有告诉业主该怎么办。
   *
   * <p>域取自 {@code basisTag} 的 release 前缀（{@code lighting@v3} → {@code lighting}）——包内单元轴是 dom-，
   * 与成文线切片口径逐字一致。调用方按 art- 传入的必挂集在两条线接通时并入本 map（并集去重）。
   */
  private static Map<String, List<String>> derivedLockedTexts(List<ReportAnchor> anchors) {
    Map<String, List<String>> lockedTexts = new TreeMap<>();
    for (ReportAnchor anchor : anchors) {
      if (NUMBER_CLASS_LOCATING.equals(anchor.numberClass())
          && anchor.provenance().annotationRequired()) {
        lockedTexts.putIfAbsent(domainOf(anchor.basisTag()), List.of(LOCKED_TEXT_SITE_CHECK));
      }
    }
    return lockedTexts;
  }

  private static String domainOf(String releaseTag) {
    int at = releaseTag.indexOf('@');
    return at < 0 ? releaseTag : releaseTag.substring(0, at);
  }

  private void resolve(
      ParameterAsset parameter,
      String releaseTag,
      EvaluationInput input,
      ArtifactEntitlement entitlement,
      LocalDate evaluatedOn,
      List<ReportAnchor> anchors,
      List<WithheldAnchor> withheld,
      List<GapRecord> gaps) {
    if (parameter.value() != null && !parameter.value().isEmpty()) {
      publish(
          parameter, releaseTag, parameter.value(), entitlement, evaluatedOn, anchors, withheld);
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
    publish(parameter, releaseTag, computed, entitlement, evaluatedOn, anchors, withheld);
  }

  /** 求值成功后的下发决定：过降档纪律，隐藏档只留审计条，其余进落点对象（规则 4.10）。 */
  private void publish(
      ParameterAsset parameter,
      String releaseTag,
      Map<String, Object> value,
      ArtifactEntitlement entitlement,
      LocalDate evaluatedOn,
      List<ReportAnchor> anchors,
      List<WithheldAnchor> withheld) {
    AnchorPresentationPolicy.Verdict verdict =
        presentationPolicy.decide(
            parameter.calibration(), parameter.numberClass(), value, entitlement);
    if (verdict.presentation() == AnchorPresentation.WITHHELD) {
      withheld.add(new WithheldAnchor(parameter.assetId(), releaseTag, verdict.withholdReason()));
      return;
    }
    anchors.add(anchor(parameter, releaseTag, value, evaluatedOn, verdict.presentation()));
  }

  /**
   * 落点对象组装。时效两字段（{@code effectiveFrom/To}）**parameters 表没有**——时效资产集中在 attributes（单价库，{@code
   * effective_*} 是实体列），造价章投影落地时由 attribute 侧填入；此处照实给 {@code null}，不为参数表预造列。
   */
  private ReportAnchor anchor(
      ParameterAsset parameter,
      String releaseTag,
      Map<String, Object> value,
      LocalDate evaluatedOn,
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
        provenancePolicy.decide(
            parameter.source(), null, null, parameter.calibration(), evaluatedOn),
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
