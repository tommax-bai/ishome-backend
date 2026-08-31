package com.ishome.project.domain.rulebook;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 求值（纯函数，无 IO）：同输入同输出（规则 8.2 可重放）。两件事——**数字**（lkp- 落点）与**关系**（规则触发）， 都在生产侧确定性算完，都不由 LLM 决定（规范
 * v2.5：关系与数字同族）。
 *
 * <p>三条求值路径：①参数带 value → 直取（formula 仅为推导说明）；②仅带 formula → 按 assetId 显式实现
 * 代入匿名输入——公式的可执行形态在此登记，未登记/输入缺失 → gap-；③无值无公式 → gap-。 结果按 lkpId 排序，数值全为整数毫米/原样单位——不引入浮点位数漂移。
 *
 * <p>落点有**两个来源**：parameters（上述三条路径）与 attributes 里 {@code entity_type=work_item} 的 单价资产（{@link
 * #projectWorkItemPrice}，规则 5.15 造价章——造价章的数字全在单价库，不在参数表）。 两者产出的落点对象形态完全一致，成文线不区分来源。
 *
 * <p>求出值之后过两道判定，都在生产侧做完、结果随包下发（成文线只执行不重判）：①**语域**（{@link AnchorPresentationPolicy}，规则
 * 4.10a/5.8）——过没过可核性门决定能不能作判断句支点；②**标注**（{@link AnchorProvenancePolicy}，规则 4.10c，v2.4
 * 新增）——未过门或已过期的落点随带 {@link AnchorProvenance}， 成文线据此在同页挂依据标注，不标即违规。
 *
 * <p>v2.4 裁决 2026-08-29 起**没有第三种去向**：求出来的值一律下发（{@code withheldAnchors} 恒空）。 隐藏这一档整体作废的理由见 {@link
 * AnchorPresentationPolicy} 与规范 §14.9。
 *
 * <p>规则的**触发判定**走另一条路（{@link #triggeredRules}）：规则不产出数字，产出"这一章该讲到什么"—— 触发成立的条目按域随包下发，判据是 {@link
 * RuleTriggerPolicy}。它与落点求值互不相干：落点求不出走 gap-， 规则没触发就是没触发，不是缺口。
 */
public final class RulebookEvaluator {

  /** 直径±区间公式的毫米余量（主厨身高/2 + [50,100]，规范 §5.2 定制尺寸族）。 */
  private static final int COUNTER_OFFSET_MIN = 50;

  private static final int COUNTER_OFFSET_MAX = 100;

  /** 定位数字（规则 2.3 数字三分法）：未过门时进正文要随页挂现场复核话术，见 {@link #derivedLockedTexts}。 */
  private static final String NUMBER_CLASS_LOCATING = "locating";

  /**
   * 单价落点的数字类别（规则 2.3 三分法）：**分析数字**——它出现在分析与正文，是造价推算的输入与结论。
   *
   * <p>不是定位数字（没人拿单价去现场画线定位，裁决 2026-08-29 的重标判据），也不是选型数字（选型数字
   * 是"驱动购买决策的**商品参数**"如色温、光束角，单价是工项行情不是商品参数）。分类挂在 entity_type 上、不逐条配：attributes 表没有 number_class
   * 列，而同一个 entity_type 的数字类别本就是同一个。
   */
  private static final String NUMBER_CLASS_ANALYSIS = "analysis";

  /** 单价资产的 entity_type（contracts {@code rulebook/attributes/work_item.schema.json}）。 */
  private static final String ENTITY_TYPE_WORK_ITEM = "work_item";

  /**
   * 单价落点的值类别（规则 1.9，v2.8）：**区间**——投影出来的恒是 {@code {min,max}}，一个匿名项。
   *
   * <p>常量而非逐条配置，理由同上面的 {@code NUMBER_CLASS_ANALYSIS}：类别是**投影规则**的属性，不是 单条单价资产的属性——attributes 表没有
   * value_kind 列，而"单价投影出什么形态"对每一条都是同一个答案。 逐条配等于把同一件事写两处，两处一旦不一致，以哪处为准没有答案。
   */
  private static final String VALUE_KIND_RANGE = "range";

  /**
   * 公式求出单值时的值类别（规则 1.9）：{@code single} = 一个匿名项，值是数。
   *
   * <p>这里**不做推断**，只做兜底：形态的权威声明是资产自己的 {@code value_kind}（种子里逐条写、核验逐条拦）。 老 release 快照没有这一列，读出来是
   * {@code null}——而契约 {@code anchors[].valueKind} 是必填字段， 下发 null 等于产出一个不合契约的包。故按求出来的**实际形态**兜底填：标量
   * → single、{@code {min,max}} → range。
   */
  private static final String VALUE_KIND_SINGLE = "single";

  /** 区间的两个边界键。**它们是项的值形态不是项**——故 {@code {lkp-x.min}} 在引用语法上不存在（规则 1.9 一）。 */
  private static final Set<String> RANGE_BOUNDS = Set.of("min", "max");

  /**
   * 单价落点的量纲前缀：单价的量纲是"**元每计价单位**"。
   *
   * <p>直接把资产的计价单位（㎡/点位/投影㎡）当落点单位下发，写作器读到的是"墙体拆除，㎡ = 20-60"—— 会被写成面积。量纲入名是本项目最重要的一条变量规则（开发规范
   * §4.1），落点单位是它的数据侧同款。
   */
  private static final String PRICE_UNIT_PREFIX = "元/";

  /** 可核性门（规则 4.10a）：过门与否同时决定降档标记与语域档位，两处判据必须同一口径。 */
  private static final String CALIBRATION_CALIBRATED = "calibrated";

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

  private final RuleTriggerPolicy triggerPolicy = new RuleTriggerPolicy();

  /** 无必挂集的求值口：调用方不产出任何要求锁定文案的 art- 时走这个重载。 */
  public ReportDataPackage evaluate(
      List<ReleaseSnapshot> snapshots,
      EvaluationInput input,
      ArtifactEntitlement entitlement,
      LocalDate evaluatedOn) {
    return evaluate(snapshots, input, entitlement, evaluatedOn, Map.of());
  }

  /**
   * 求值并合成报告数据包。
   *
   * <p>{@code lockedTextsByArtifact} = **调用方按 art- 传入的必挂锁定文案 ID 集**（域 → ID 列表，域取去前缀形态）： 与 {@code
   * entitlement} 同一条理由入参——art- 产物清单连同它的必挂列住在 contracts，本模块禁止复制该表 （规则 4.12），谁调用谁知道自己在生成哪个产物。它与
   * {@link #derivedLockedTexts} 求**并集**下发（裁决⑯：必挂集以数据包清单为唯一口径）。
   */
  public ReportDataPackage evaluate(
      List<ReleaseSnapshot> snapshots,
      EvaluationInput input,
      ArtifactEntitlement entitlement,
      LocalDate evaluatedOn,
      Map<String, List<String>> lockedTextsByArtifact) {
    List<ReleaseSnapshot> ordered =
        snapshots.stream().sorted(Comparator.comparing(ReleaseSnapshot::domain)).toList();
    List<ReportAnchor> anchors = new ArrayList<>();
    List<GapRecord> gaps = new ArrayList<>();
    Map<String, List<PersonaAsset>> personas = new TreeMap<>();
    Map<String, List<CheckAsset>> checks = new TreeMap<>();
    Map<String, List<TriggeredRule>> triggeredRules = new TreeMap<>();
    Map<String, List<String>> bannedTerms = new TreeMap<>();
    // 禁词分组随包下发（2026-08-30）：平表照旧（扫描与守卫要它），分组另给一份供打回话分句用。
    Map<String, Map<String, List<String>>> bannedTermGroups = new TreeMap<>();
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
      bannedTermGroups.put(snapshot.domain(), snapshot.bannedTermGroups());
      triggeredRules.put(snapshot.domain(), triggeredRules(snapshot, input));
      for (ParameterAsset parameter : snapshot.parameters()) {
        resolve(parameter, snapshot, input, evaluatedOn, anchors, gaps);
      }
      for (AttributeAsset attribute : snapshot.attributes()) {
        projectWorkItemPrice(attribute, snapshot.releaseTag(), input, evaluatedOn, anchors, gaps);
        projectWorkItemCost(attribute, snapshot.releaseTag(), input, evaluatedOn, anchors, gaps);
      }
    }
    anchors.sort(Comparator.comparing(ReportAnchor::lkpId));
    gaps.sort(Comparator.comparing(GapRecord::lkpId));
    return new ReportDataPackage(
        evaluatedOn,
        entitlement,
        ordered.stream().map(ReleaseSnapshot::domain).toList(),
        ordered.stream().map(ReleaseSnapshot::ref).toList(),
        List.copyOf(anchors),
        // withheldAnchors：v2.4 取消隐藏档后恒空，字段按契约"只增不删"保留（规范 §14.9）
        List.of(),
        List.copyOf(gaps),
        personas,
        checks,
        triggeredRules,
        bannedTerms,
        bannedTermGroups,
        mergedLockedTexts(derivedLockedTexts(anchors), lockedTextsByArtifact),
        input);
  }

  /**
   * 本域触发成立的规则条目（规范 §4.1 三层三触发；判据见 {@link RuleTriggerPolicy}）。
   *
   * <p>域键**恒存在**（哪怕本域一条没触发，值也是空列表）：与 personas/checks 同形态——"这一域评过了、结论是没有"
   * 与"这一域根本没评"是两件事，缺键会让消费侧把前者读成后者。按 assetId 排序，同输入字节级同输出（规则 8.2）。
   *
   * <p>不下发未触发的条目：成文线的输入是"已经成立的规则"，把触发条件一起给过去就等于请它重判一遍 （同"成文线不重判求值线"）。
   */
  private List<TriggeredRule> triggeredRules(ReleaseSnapshot snapshot, EvaluationInput input) {
    List<TriggeredRule> triggered = new ArrayList<>();
    for (RuleAsset rule : snapshot.rules()) {
      triggerPolicy
          .decide(rule, input.layoutFeatures())
          .ifPresent(
              evidence ->
                  triggered.add(
                      new TriggeredRule(
                          rule.assetId(),
                          rule.layer(),
                          rule.content(),
                          rule.rationale(),
                          rule.severity(),
                          rule.calibration(),
                          evidence)));
    }
    triggered.sort(Comparator.comparing(TriggeredRule::assetId));
    return List.copyOf(triggered);
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

  /**
   * 必挂锁定文案的并集（求值线派生 ∪ 调用方按 art- 传入）。
   *
   * <p>并集不是覆盖：两侧各自成立且理由不同——派生那半来自**落点的结构化属性**（未过门定位数字要挂现场复核话术， 规则
   * 4.10c），传入那半来自**产物本身的必挂列**（如造价章的免责）。任一侧漏挂都是纪律失效，多挂只是页脚多一行 （规则 4.10c "标注必挂"的同一条不对称）。
   *
   * <p>域内 ID 去重后排序、域键用 {@link TreeMap}：同输入字节级同输出（规则 8.2），否则并集顺序随入参 map 的迭代序漂移。
   */
  private static Map<String, List<String>> mergedLockedTexts(
      Map<String, List<String>> derived, Map<String, List<String>> byArtifact) {
    Map<String, List<String>> merged = new TreeMap<>(derived);
    byArtifact.forEach(
        (domain, ids) -> {
          TreeSet<String> union = new TreeSet<>(merged.getOrDefault(domain, List.of()));
          union.addAll(ids);
          merged.put(domain, List.copyOf(union));
        });
    merged.replaceAll((domain, ids) -> List.copyOf(new TreeSet<>(ids)));
    return merged;
  }

  private static String domainOf(String releaseTag) {
    int at = releaseTag.indexOf('@');
    return at < 0 ? releaseTag : releaseTag.substring(0, at);
  }

  /**
   * work_item 单价资产 → 落点投影（规则 5.15 造价章"分项造价区间 = 量 × 单价区间"的单价那一半）。
   *
   * <p>为什么要投影而不是在 parameters 里手写一份镜像：单价是**时效资产**，治理头（两源交叉验证、 effective 时效、置信定区间宽度）全在 attributes
   * 表；写一份 lkp- 镜像等于把同一个数放两处， 新增一条单价时镜像忘了加就是造价章静默少一项。投影是全域一致的结构规则，加一条单价 = 加一行数据。
   *
   * <p>id 换前缀而非另起名：{@code attr-} 是单价库资产（全国/分档、带两源与时效），{@code lkp-} 是本户 按城市档选出的那个区间——关系同 parameters
   * 的"公式资产 → 代入匿名输入后的落点"，不是同概念两套名 （规则 1.8 第四条）。只投影 {@code work_item}：material/color/storage_item
   * 是描述性属性，投影它们 只会造出一批没有值形态的落点。
   */
  private void projectWorkItemPrice(
      AttributeAsset attribute,
      String releaseTag,
      EvaluationInput input,
      LocalDate evaluatedOn,
      List<ReportAnchor> anchors,
      List<GapRecord> gaps) {
    if (!ENTITY_TYPE_WORK_ITEM.equals(attribute.entityType())) {
      return;
    }
    String lkpId = anchorIdOf(attribute.assetId());
    Map<String, Object> value = priceRange(attribute.props(), input.cityTier());
    if (value == null) {
      gaps.add(new GapRecord(lkpId, releaseTag, "empty_definition", "单价资产无 price_range 区间"));
      return;
    }
    Object unit = attribute.props().get("unit");
    anchors.add(
        new ReportAnchor(
            lkpId,
            attribute.name(),
            NUMBER_CLASS_ANALYSIS,
            unit == null ? null : PRICE_UNIT_PREFIX + unit,
            VALUE_KIND_RANGE,
            value,
            // 单价没有参考平面：那是照度一类"在哪个面上量"的量才有的元信息，编一个反而给标注层造假
            null,
            releaseTag,
            attribute.source(),
            attribute.calibration(),
            isDegraded(attribute.calibration()),
            provenancePolicy.decide(
                attribute.source(),
                attribute.effectiveFrom(),
                attribute.effectiveTo(),
                attribute.calibration(),
                evaluatedOn),
            presentationPolicy.decide(attribute.calibration())));
  }

  /**
   * 单价 × 这一户的量 = **金额**（{@code lkp-cost-*}）。报告里第一笔真的钱。
   *
   * <p>立案（2026-08-31）：造价章有五条 calibrated 单价却算不出任何总价——**缺的从来不是单价，
   * 是量**。而"有单位不等于有量"：五条里三条的 {@code unit} 字面都是「㎡」，但拆除的㎡是被拆
   * 墙体面积、涂刷的㎡是墙面展开面积、水电人工的㎡是**建筑面积**，是三个完全不同的量。
   *
   * <p>所以乘哪个量**由数据说**（单价资产的 {@code props.quantity_basis}，取值是匿名画像里那个量
   * 的字段名），不由代码猜资产名、也不在这里硬编一张 id→量 的表——红线：配置只放数据，逻辑归服务。
   * 没声明 {@code quantity_basis} 的资产**不产金额**（现在其余四条都是这样：它们的量还不存在），
   * 也**不记 gap-**：那不是"求不出"，是这条产物压根还没被设计出来，记 gap- 等于向报告承诺一个
   * 我们没打算给的数。
   */
  private void projectWorkItemCost(
      AttributeAsset attribute,
      String releaseTag,
      EvaluationInput input,
      LocalDate evaluatedOn,
      List<ReportAnchor> anchors,
      List<GapRecord> gaps) {
    if (!ENTITY_TYPE_WORK_ITEM.equals(attribute.entityType())) {
      return;
    }
    Object basis = attribute.props().get("quantity_basis");
    if (basis == null) {
      return;
    }
    String costId = costIdOf(attribute.assetId());
    Double quantity = quantityOf(basis.toString(), input);
    if (quantity == null) {
      gaps.add(
          new GapRecord(
              costId, releaseTag, "missing_input", "缺" + basis + "，算不出这一项的钱"));
      return;
    }
    Map<String, Object> price = priceRange(attribute.props(), input.cityTier());
    if (price == null
        || !(price.get("min") instanceof Number min)
        || !(price.get("max") instanceof Number max)) {
      gaps.add(new GapRecord(costId, releaseTag, "empty_definition", "单价资产无 price_range 区间"));
      return;
    }
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("min", Math.round(quantity * min.doubleValue()));
    value.put("max", Math.round(quantity * max.doubleValue()));
    anchors.add(
        new ReportAnchor(
            costId,
            attribute.name() + "合计",
            NUMBER_CLASS_ANALYSIS,
            "元",
            VALUE_KIND_RANGE,
            value,
            null,
            releaseTag,
            "求值线按「单价 × 量」算出：单价 "
                + price.get("min")
                + "–"
                + price.get("max")
                + " 元/"
                + attribute.props().get("unit")
                + "（城市档 "
                + (input.cityTier() == null ? "全国粗档" : input.cityTier())
                + "）× "
                + basis
                + " "
                + quantity
                + "。单价是经验条目、区间本身就宽，故金额区间随之宽。",
            attribute.calibration(),
            isDegraded(attribute.calibration()),
            provenancePolicy.decide(
                attribute.source(),
                attribute.effectiveFrom(),
                attribute.effectiveTo(),
                attribute.calibration(),
                evaluatedOn),
            presentationPolicy.decide(attribute.calibration())));
  }

  /** 画像里那个量。闭集随量的到位逐条增；认不出的名字返回 null（当作没这个量，不猜）。 */
  private static Double quantityOf(String basis, EvaluationInput input) {
    return switch (basis) {
      case "building_area_sqm" -> input.buildingAreaSqm();
      case "net_area_sqm" -> input.netAreaSqm();
      default -> null;
    };
  }

  /** {@code attr-price-hydro-labor-sqm} → {@code lkp-cost-hydro-labor-sqm}（金额与单价分属两个落点）。 */
  private static String costIdOf(String assetId) {
    return assetId.startsWith("attr-price-")
        ? "lkp-cost-" + assetId.substring("attr-price-".length())
        : assetId + "-cost";
  }

  /** {@code attr-price-demolition} → {@code lkp-price-demolition}（契约 anchors[].lkpId 恒 lkp- 前缀）。 */
  private static String anchorIdOf(String assetId) {
    return assetId.startsWith("attr-") ? "lkp-" + assetId.substring("attr-".length()) : assetId;
  }

  /**
   * 单价选档：城市档**逐字命中** {@code breakdown} 的键且该档是二元数值区间 → 取该档；否则取 {@code price_range}（全国粗档）。
   *
   * <p>逐字命中不做任何归一/映射：档名是数据自带的词面（"一线"/"二线"/"三四线"），映射表一旦存在就会 与数据漂移。命不中是常态而非异常——{@code breakdown}
   * 也可能按墙体类型或档位细分（"普通墙"、"经济"）， 那时全国粗档区间就是这条单价的正确答案，不是降级。
   */
  private static Map<String, Object> priceRange(Map<String, Object> props, String cityTier) {
    Object band = null;
    if (cityTier != null && props.get("breakdown") instanceof Map<?, ?> breakdown) {
      band = breakdown.get(cityTier);
    }
    Map<String, Object> tiered = rangeOf(band);
    return tiered != null ? tiered : rangeOf(props.get("price_range"));
  }

  /** {@code [low, high]} 二元数值数组 → {@code {min,max}}；单值档位边界（如"高定下限"）等其余形态 → null。 */
  private static Map<String, Object> rangeOf(Object band) {
    if (!(band instanceof List<?> pair)
        || pair.size() != 2
        || !(pair.get(0) instanceof Number)
        || !(pair.get(1) instanceof Number)) {
      return null;
    }
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("min", pair.get(0));
    value.put("max", pair.get(1));
    return value;
  }

  /** 未过可核性门（规则 4.10a）：降档标记与语域档位的同一口径。 */
  private static boolean isDegraded(String calibration) {
    return !CALIBRATION_CALIBRATED.equals(calibration);
  }

  private void resolve(
      ParameterAsset parameter,
      ReleaseSnapshot snapshot,
      EvaluationInput input,
      LocalDate evaluatedOn,
      List<ReportAnchor> anchors,
      List<GapRecord> gaps) {
    String releaseTag = snapshot.releaseTag();
    if (hasValue(parameter.value())) {
      anchors.add(anchor(parameter, releaseTag, parameter.value(), evaluatedOn));
      return;
    }
    if (parameter.formula() == null || parameter.formula().isBlank()) {
      gaps.add(new GapRecord(parameter.assetId(), releaseTag, "empty_definition", "参数无值无公式"));
      return;
    }
    Object computed =
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
          // 全屋收纳总长 = 套内面积 × 收纳密度基准。**报告里第一条真的"量"**（2026-08-31）。
          //
          // 立案：真跑实测造价章有五条 calibrated 单价却算不出任何总价，收纳章说不出全屋要多少米
          // 收纳——缺的从来不是单价，是量。而这一条的量，靠业主自己知道的两个数就够了
          // （建筑面积 × 得房率 = 套内面积），**不必等定稿平面**。
          //
          // 与该资产原本声明的公式「Σ 各柜体投影沿墙长度（从定稿平面求得）」是**同一个概念的两条
          // 精度**，不是两套名（规则 1.8 第四条禁同概念两套名）：现在这条按面积推算，来源＝经验默认
          // 值、区间宽；定稿平面接通后按柜体实算，来源＝图纸推算、区间窄。**区间宽度由来源继承**，
          // 于是"这个数什么时候会收窄"有一个真实的答案：布局求解接进来的时候。
          // ★ 执行者提议，非用户裁决：用粗公式先出这个数、而不是继续记 gap-。
          case "lkp-storage-total-meters" -> storageTotalMeters(snapshot, input);
          default -> null;
        };
    if (computed == null) {
      boolean implemented =
          switch (parameter.assetId()) {
            case "lkp-counter-height",
                    "lkp-wardrobe-rod",
                    "lkp-mirror-height",
                    "lkp-tv-distance",
                    "lkp-storage-total-meters" ->
                true;
            default -> false;
          };
      gaps.add(
          new GapRecord(
              parameter.assetId(),
              releaseTag,
              implemented ? "missing_input" : "formula_not_implemented",
              parameter.formula()));
      return;
    }
    anchors.add(
        anchor(parameter, releaseTag, computed, evaluatedOn, derivationOf(parameter, input)));
  }

  /**
   * 这个数是怎么算出来的——**如实写，写不出就返回资产原本的 source，绝不编**。
   *
   * <p>射程只覆盖求值线自己实现了公式的那几条：它们的推导在代码里，代码知道就该说出来。
   */
  private static String derivationOf(ParameterAsset parameter, EvaluationInput input) {
    return switch (parameter.assetId()) {
      case "lkp-storage-total-meters" ->
          "求值线按公式算出：套内面积 "
              + input.netAreaSqm()
              + " ㎡（建筑面积 "
              + input.buildingAreaSqm()
              + " ㎡ × 得房率 "
              + input.floorAreaRatioPercent()
              + "%）× 收纳密度基准（米/㎡）。密度基准是经验条目、无外部源，故本条区间偏宽；"
              + "定稿平面接通后改按各柜体投影沿墙长度实算，区间随之收窄。";
      default -> parameter.source();
    };
  }

  /**
   * 求值成功后的落点对象组装：**求出来的一律下发**（v2.4 起没有隐藏这条去向），两道判定的结果随对象走。
   *
   * <p>时效两字段（{@code effectiveFrom/To}）**parameters 表没有**——时效资产集中在 attributes（单价库，{@code
   * effective_*} 是实体列），造价章投影落地时由 attribute 侧填入；此处照实给 {@code null}，不为参数表预造列。
   */
  private ReportAnchor anchor(
      ParameterAsset parameter, String releaseTag, Object value, LocalDate evaluatedOn) {
    return anchor(parameter, releaseTag, value, evaluatedOn, parameter.source());
  }

  /**
   * 落点组装，{@code source} 可由调用方覆写——**公式求出来的数要说清它是怎么来的**。
   *
   * <p>立案（2026-08-31 真跑）：收纳总长第一次算出来（88㎡ × 0.25–0.35 = 22.0–30.8 米）并写进了
   * 正文，但同一句话里跟着一句**编的**解释——「这个范围锚定的是当前囤货节奏与墙面可嵌入家具形态
   * 的交集」。查下去，这条算出来的落点 {@code source} 是 null：**我们没告诉写作步这个数怎么来的，
   * 它就自己编了一个**。数字是真的、解释是假的，比两个都假更危险。
   *
   * <p>所以公式求值这一支必须把推导原样带上（哪个输入、乘了哪条系数），标注层与写作步共用它。
   */
  private ReportAnchor anchor(
      ParameterAsset parameter,
      String releaseTag,
      Object value,
      LocalDate evaluatedOn,
      String source) {
    return new ReportAnchor(
        parameter.assetId(),
        parameter.name(),
        parameter.numberClass(),
        parameter.unit(),
        valueKindOf(parameter, value),
        value,
        parameter.referencePlane(),
        releaseTag,
        source,
        parameter.calibration(),
        isDegraded(parameter.calibration()),
        provenancePolicy.decide(source, null, null, parameter.calibration(), evaluatedOn),
        presentationPolicy.decide(parameter.calibration()));
  }

  /**
   * 落点的值类别：**以资产的声明为准**，缺席时按求出来的实际形态兜底（规则 1.9）。
   *
   * <p>声明优先不是客气话：{@code scenario} 与 {@code component} 的 value 形态一模一样（都是项名 → 数），
   * 差别只在项名走哪份受控词表——从值的形状根本推不出来。兜底只覆盖推得出的那两种（标量 → {@code single}、 {@code {min,max}} → {@code
   * range}），且只在老快照缺列时起作用；推不出就照实给 {@code null}， 不猜一个类别混过契约（猜错的类别会让成文线按错误的词表校项名，比缺字段更难查）。
   */
  private static String valueKindOf(ParameterAsset parameter, Object value) {
    if (parameter.valueKind() != null && !parameter.valueKind().isBlank()) {
      return parameter.valueKind();
    }
    if (value instanceof Number) {
      return VALUE_KIND_SINGLE;
    }
    if (value instanceof Map<?, ?> map
        && !map.isEmpty()
        && RANGE_BOUNDS.containsAll(map.keySet())) {
      return VALUE_KIND_RANGE;
    }
    return null;
  }

  /** 空 Map 与 null 都算"没有值"：快照里 {@code value: {}} 与缺席是同一件事，都走公式或 gap-。 */
  private static boolean hasValue(Object value) {
    if (value == null) {
      return false;
    }
    return !(value instanceof Map<?, ?> map) || !map.isEmpty();
  }

  /**
   * 全屋收纳总长（米）= 套内面积（㎡）× 收纳密度基准（米/㎡）。
   *
   * <p>密度基准取**同一份 release 快照内**的 {@code lkp-storage-density-baseline}——同域同版，
   * 不跨域取值（跨域取即在求值线内部造出章与章的依赖，正是编排侧刻意避开的耦合）。
   *
   * <p>任一输入缺席返回 {@code null}：**拿不到就说没有，不填猜的值**，由调用方记 {@code missing_input}。
   */
  private static Map<String, Object> storageTotalMeters(
      ReleaseSnapshot snapshot, EvaluationInput input) {
    Double netArea = input.netAreaSqm();
    if (netArea == null) {
      return null;
    }
    Map<String, Object> density =
        snapshot.parameters().stream()
            .filter(p -> "lkp-storage-density-baseline".equals(p.assetId()))
            .map(ParameterAsset::value)
            .filter(v -> v instanceof Map<?, ?>)
            .map(v -> {
              @SuppressWarnings("unchecked")
              Map<String, Object> m = (Map<String, Object>) v;
              return m;
            })
            .findFirst()
            .orElse(null);
    if (density == null
        || !(density.get("min") instanceof Number min)
        || !(density.get("max") instanceof Number max)) {
      return null;
    }
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("min", round1(netArea * min.doubleValue()));
    value.put("max", round1(netArea * max.doubleValue()));
    return value;
  }

  /** 一位小数：收纳长度按米给，再多的位数是求值线自造精度（规则 4.10e 禁）。 */
  private static double round1(double v) {
    return Math.round(v * 10.0) / 10.0;
  }

  private static Map<String, Object> range(int min, int max) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("min", min);
    value.put("max", max);
    return value;
  }

  /**
   * 公式求出的单值：**标量**，不再包 {@code {v: …}} 壳。
   *
   * <p>{@code v} 是无语义键（规则 1.7 禁），而 v2.8 的两层模型里"一个匿名项，值是数"的形态就是标量本身—— 壳一旦在，{@code {lkp-x.v}}
   * 就是写得出来的引用，那正是这次要用结构堵死的东西。
   */
  private static Long point(long v) {
    return v;
  }
}
