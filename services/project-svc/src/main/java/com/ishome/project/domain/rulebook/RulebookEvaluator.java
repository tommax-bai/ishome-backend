package com.ishome.project.domain.rulebook;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * <p>公式求出的点值**按资产自己声明的粒度取整**（{@link ParameterAsset#roundTo()}，规则 4.10e 增补，用户裁决 2026-09-08）：
 * 取整、不取整都由数据说，求值线只执行；不声明即不取整；区间两端各自取整；推导原文写出"2136，取整到 10 mm ＝ 2140"。
 * 落点对象带的是取整后的值，故任何读落点的下游公式拿到的都是取整后的值（业主看到的输入输出对得上）。渲染层一字不动。 单价 × 量的派生金额同理，粒度由单价资产的 {@code
 * props.cost_round_to} 声明（元）。
 *
 * <p>金额之上再派生两样（用户裁决 2026-09-09「占比只由算得」，规则 5.15 第 2 节 v2.13）：①**占比**（{@code lkp-share-*}）＝ 该项金额 ÷
 * 总额，两端各自算（min＝金额 min ÷ 总额 max，max＝金额 max ÷ 总额 min），分母由单价资产的 {@code props.share_of} 指名；
 * ②**三档合计**（{@code lkp-cost-*-by-grade}）＝ 单价资产自带的 {@code props.grade_breakdown} 各档 × 量。两样都不用搜来的
 * "大家装修时的占比"与倍数；没有金额的分项占比记 gap-（"等平面出来按量算"，规则 4.18），不填。
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
        projectWorkItemCostByGrade(
            attribute, snapshot.releaseTag(), input, evaluatedOn, anchors, gaps);
      }
      // 占比要等本域全部金额算完再除：分母（总额）与分子（分项）都是上一步的产物
      for (AttributeAsset attribute : snapshot.attributes()) {
        projectWorkItemShare(attribute, snapshot, evaluatedOn, anchors, gaps);
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
   * <p>立案（2026-08-31）：造价章有五条 calibrated 单价却算不出任何总价——**缺的从来不是单价， 是量**。而"有单位不等于有量"：五条里三条的 {@code
   * unit} 字面都是「㎡」，但拆除的㎡是被拆 墙体面积、涂刷的㎡是墙面展开面积、水电人工的㎡是**建筑面积**，是三个完全不同的量。
   *
   * <p>所以乘哪个量**由数据说**（单价资产的 {@code props.quantity_basis}，取值是匿名画像里那个量 的字段名），不由代码猜资产名、也不在这里硬编一张 id→量
   * 的表——红线：配置只放数据，逻辑归服务。 没声明 {@code quantity_basis} 的资产**不产金额**（现在其余四条都是这样：它们的量还不存在）， 也**不记
   * gap-**：那不是"求不出"，是这条产物压根还没被设计出来，记 gap- 等于向报告承诺一个 我们没打算给的数。
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
      gaps.add(new GapRecord(costId, releaseTag, "missing_input", "缺" + basis + "，算不出这一项的钱"));
      return;
    }
    Map<String, Object> price = priceRange(attribute.props(), input.cityTier());
    if (price == null
        || !(price.get("min") instanceof Number min)
        || !(price.get("max") instanceof Number max)) {
      gaps.add(new GapRecord(costId, releaseTag, "empty_definition", "单价资产无 price_range 区间"));
      return;
    }
    // 元是最小记数单位：先到元（不到元就是 5983.999… 这种浮点尾巴），再按声明取整（估算类金额到百元）
    Map<String, Object> exact = new LinkedHashMap<>();
    exact.put("min", Math.round(quantity * min.doubleValue()));
    exact.put("max", Math.round(quantity * max.doubleValue()));
    Double costRoundTo =
        attribute.props().get(COST_ROUND_TO_KEY) instanceof Number g ? g.doubleValue() : null;
    Object value = roundTo(exact, costRoundTo);
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
                + " ＝ "
                + text(exact)
                + roundingClause(value, costRoundTo, "元")
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

  /** 单价资产声明的派生金额取整粒度（元）；缺席即不取整（规则 4.10e 增补）。 */
  private static final String COST_ROUND_TO_KEY = "cost_round_to";

  /** 单价资产自带的档位单价表（低/中/高三档），键取 tier 闭集（规则 1.9 三），值是 {@code [low, high]}。 */
  private static final String GRADE_BREAKDOWN_KEY = "grade_breakdown";

  /** 档位闭集**有序**：low < medium < high（contracts anchor_items §两层模型；三档合计的项按此序下发）。 */
  private static final List<String> TIER_ITEMS = List.of("low", "medium", "high");

  /** 三档合计的值类别：每档一项、项名取 tier 闭集，一项的值是区间。 */
  private static final String VALUE_KIND_TIER = "tier";

  /** 占比的分母：本分项占**哪一条**单价资产派生金额的比——数据指名，代码不猜"哪条是总额"。 */
  private static final String SHARE_OF_KEY = "share_of";

  /** 占比落点的题名（业主话）：拼两条资产名会拼出一句读不通的话，故由数据给。 */
  private static final String SHARE_NAME_KEY = "share_name";

  /** 占比的取整粒度（百分点）；缺席即不取整。 */
  private static final String SHARE_ROUND_TO_KEY = "share_round_to";

  /** 占比的记数单位。 */
  private static final String SHARE_UNIT = "%";

  /**
   * 占比的最小记数位：**百分之一个百分点**。商是无限小数，总得停在某一位——同金额"先到元再按声明取整"，占比先到 0.01 个百分点 再按 {@code share_round_to}
   * 取整；推导原文里取整前的数也印到这一位。
   */
  private static final int SHARE_SCALE = 2;

  /**
   * 单价资产自带三档 × 这一户的量 = **三档合计**（{@code lkp-cost-*-by-grade}）。
   *
   * <p>用户裁决 2026-09-09：三档＝单价资产自带的低中高三档 × 量，**不用搜来的倍数**（原 lkp-budget-tier-gap 退役）。 档位单价是单价资产的 {@code
   * grade_breakdown}，键必须是 tier 闭集的三个名（核验拦，此处只认闭集内的键）；量与取整粒度和金额那条完全同源 （{@code quantity_basis} /
   * {@code cost_round_to}）。没有 {@code grade_breakdown} 的资产不产、也不记 gap-（同金额：没设计的产物不承诺）；
   * 有档表却没量的，金额那条已记了 missing_input，这里不再记第二条。
   *
   * <p>档表是不是分城市的，由它的结构说：{@code grade_breakdown} 只有档一维、没有城市一维，推导原文如实写"不分城市"。
   */
  private void projectWorkItemCostByGrade(
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
    if (basis == null
        || !(attribute.props().get(GRADE_BREAKDOWN_KEY) instanceof Map<?, ?> grades)) {
      return;
    }
    Double quantity = quantityOf(basis.toString(), input);
    if (quantity == null) {
      return;
    }
    String gradeId = costIdOf(attribute.assetId()) + "-by-grade";
    Map<String, Object> unitPrices = new LinkedHashMap<>();
    Map<String, Object> exact = new LinkedHashMap<>();
    for (String tier : TIER_ITEMS) {
      Map<String, Object> band = rangeOf(grades.get(tier));
      if (band == null) {
        continue;
      }
      unitPrices.put(tier, band);
      Map<String, Object> amount = new LinkedHashMap<>();
      amount.put("min", Math.round(quantity * ((Number) band.get("min")).doubleValue()));
      amount.put("max", Math.round(quantity * ((Number) band.get("max")).doubleValue()));
      exact.put(tier, amount);
    }
    if (exact.isEmpty()) {
      gaps.add(new GapRecord(gradeId, releaseTag, "empty_definition", "档位单价表没有一档是 tier 闭集内的二元区间"));
      return;
    }
    Double costRoundTo =
        attribute.props().get(COST_ROUND_TO_KEY) instanceof Number g ? g.doubleValue() : null;
    Map<String, Object> value = new LinkedHashMap<>();
    exact.forEach((tier, amount) -> value.put(tier, roundTo(amount, costRoundTo)));
    anchors.add(
        new ReportAnchor(
            gradeId,
            attribute.name() + "分三档合计",
            NUMBER_CLASS_ANALYSIS,
            "元",
            VALUE_KIND_TIER,
            value,
            null,
            releaseTag,
            "求值线按「各档单价 × 量」算出（档位是单价资产自带的三档，只有档一维、不分城市）：档位单价 "
                + tierText(unitPrices)
                + " 元/"
                + attribute.props().get("unit")
                + " × "
                + basis
                + " "
                + quantity
                + " ＝ "
                + tierText(exact)
                + (costRoundTo == null
                    ? ""
                    : "，取整到 " + text(costRoundTo) + " 元 ＝ " + tierText(value))
                + "。三档差在哪只由这三个区间说，不引用任何搜来的倍数。",
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

  /** 推导原文里的档位表："low 800–1200 / medium 1200–1800 / high 2000–3000"。 */
  private static String tierText(Map<String, Object> byTier) {
    StringBuilder sb = new StringBuilder();
    byTier.forEach(
        (tier, v) -> {
          if (!sb.isEmpty()) {
            sb.append(" / ");
          }
          sb.append(tier).append(' ').append(text(v));
        });
    return sb.toString();
  }

  /**
   * 分项金额 ÷ 总额 = **占比**（{@code lkp-share-*}）。用户裁决 2026-09-09：占比只由算得，不由搜得。
   *
   * <p>分母由数据指名（{@code props.share_of} = 总额那条单价资产的 id）：哪一条是"总额"不是代码能猜的事——它与 {@code quantity_basis}
   * 同一条理由（配置只放数据，逻辑归服务）。两端各自算：min＝分项 min ÷ 总额 max，max＝分项 max ÷ 总额 min，
   * 区间随两侧的宽度一起宽，不缩。除的是**取整后的金额落点**（业主看到的输入输出对得上）。
   *
   * <p>声明了 {@code share_of} 却算不出金额的分项（量还不存在：拆除面积/点位数/柜体投影/涂刷面积等定稿平面）**记 gap-、不填**—— 这与金额那条"没设计的产物不记
   * gap-"不冲突：{@code share_of} 写上去，这条产物就是设计了的，缺的是量，按规则 4.18 坦白 "等平面出来按量算"。总额自己算不出时同样记 gap-（分母都没有）。
   *
   * <p>可核性与时效取两条资产的**交集**：两条都 calibrated 才 calibrated；时效窗取两窗的交（起取晚者、止取早者）——占比不可能比 它的任一输入更硬、更新。
   */
  private void projectWorkItemShare(
      AttributeAsset attribute,
      ReleaseSnapshot snapshot,
      LocalDate evaluatedOn,
      List<ReportAnchor> anchors,
      List<GapRecord> gaps) {
    if (!ENTITY_TYPE_WORK_ITEM.equals(attribute.entityType())) {
      return;
    }
    Object shareOf = attribute.props().get(SHARE_OF_KEY);
    if (shareOf == null) {
      return;
    }
    String releaseTag = snapshot.releaseTag();
    String shareId = shareIdOf(attribute.assetId());
    ReportAnchor part = anchorById(anchors, costIdOf(attribute.assetId()));
    if (part == null) {
      gaps.add(
          new GapRecord(shareId, releaseTag, "missing_input", "等平面出来按量算：这一项的量还没有，金额算不出，占比也就没有"));
      return;
    }
    ReportAnchor total = anchorById(anchors, costIdOf(shareOf.toString()));
    AttributeAsset totalAsset =
        snapshot.attributes().stream()
            .filter(a -> a.assetId().equals(shareOf.toString()))
            .findFirst()
            .orElse(null);
    if (total == null || totalAsset == null) {
      gaps.add(
          new GapRecord(shareId, releaseTag, "missing_input", "分母 " + shareOf + " 的金额算不出，占比没有分母"));
      return;
    }
    if (!(part.value() instanceof Map<?, ?> p)
        || !(total.value() instanceof Map<?, ?> t)
        || !(p.get("min") instanceof Number partMin)
        || !(p.get("max") instanceof Number partMax)
        || !(t.get("min") instanceof Number totalMin)
        || !(t.get("max") instanceof Number totalMax)
        || totalMin.doubleValue() <= 0
        || totalMax.doubleValue() <= 0) {
      gaps.add(new GapRecord(shareId, releaseTag, "empty_definition", "分项或分母金额不是正区间"));
      return;
    }
    Map<String, Object> exact = new LinkedHashMap<>();
    exact.put("min", percent(partMin, totalMax));
    exact.put("max", percent(partMax, totalMin));
    Double shareRoundTo =
        attribute.props().get(SHARE_ROUND_TO_KEY) instanceof Number g ? g.doubleValue() : null;
    Object value = roundTo(exact, shareRoundTo);
    String calibration =
        CALIBRATION_CALIBRATED.equals(attribute.calibration())
                && CALIBRATION_CALIBRATED.equals(totalAsset.calibration())
            ? CALIBRATION_CALIBRATED
            : "draft";
    Object shareName = attribute.props().get(SHARE_NAME_KEY);
    anchors.add(
        new ReportAnchor(
            shareId,
            shareName == null ? part.name() + "占" + total.name() + "的比例" : shareName.toString(),
            NUMBER_CLASS_ANALYSIS,
            SHARE_UNIT,
            VALUE_KIND_RANGE,
            value,
            null,
            releaseTag,
            "求值线按「分项金额 ÷ 合计金额」算出（两端各自算：min＝分项 min ÷ 合计 max，max＝分项 max ÷ 合计 min）："
                + part.name()
                + " "
                + text(part.value())
                + " 元 ÷ "
                + total.name()
                + " "
                + text(total.value())
                + " 元 ＝ "
                + text(exact)
                + "%"
                + roundingClause(value, shareRoundTo, SHARE_UNIT)
                + "。两边都是宽区间，占比区间随之宽；不引用任何公开行情里\"大家装修时的占比\"。",
            calibration,
            isDegraded(calibration),
            // 标注层印的出处是两条单价资产各自的外部来源（分子与分母），不是求值线自己的推导句
            provenancePolicy.decide(
                attribute.source() + "；合计来源：" + totalAsset.source(),
                laterOf(attribute.effectiveFrom(), totalAsset.effectiveFrom()),
                earlierOf(attribute.effectiveTo(), totalAsset.effectiveTo()),
                calibration,
                evaluatedOn),
            presentationPolicy.decide(calibration)));
  }

  /** 分项 ÷ 合计 × 100，停在 {@link #SHARE_SCALE} 位：2.00 / 6.82（下发前再按声明取整）。 */
  private static double percent(Number part, Number total) {
    return BigDecimal.valueOf(part.doubleValue())
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(total.doubleValue()), SHARE_SCALE, RoundingMode.HALF_UP)
        .doubleValue();
  }

  private static ReportAnchor anchorById(List<ReportAnchor> anchors, String lkpId) {
    for (ReportAnchor anchor : anchors) {
      if (anchor.lkpId().equals(lkpId)) {
        return anchor;
      }
    }
    return null;
  }

  private static LocalDate laterOf(LocalDate a, LocalDate b) {
    if (a == null || b == null) {
      return a == null ? b : a;
    }
    return a.isAfter(b) ? a : b;
  }

  private static LocalDate earlierOf(LocalDate a, LocalDate b) {
    if (a == null || b == null) {
      return a == null ? b : a;
    }
    return a.isBefore(b) ? a : b;
  }

  /** {@code attr-price-hydro-labor-sqm} → {@code lkp-share-hydro-labor-sqm}（占比与金额、单价各一条落点）。 */
  private static String shareIdOf(String assetId) {
    return assetId.startsWith("attr-price-")
        ? "lkp-share-" + assetId.substring("attr-price-".length())
        : assetId + "-share";
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
    if (parameter.retired()) {
      // 已裁定不再下发（V9 status=retired）：不产落点、也不记 gap-——gap- 是"求不出"，这是"不给"
      return;
    }
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
    // 取整按资产自己的声明（规则 4.10e 增补）：不声明就原样下发；落点带的是取整后的值，下游读到的也是它
    Object delivered = roundTo(computed, parameter.roundTo());
    anchors.add(
        anchor(
            parameter,
            releaseTag,
            delivered,
            evaluatedOn,
            provenanceOf(parameter, derivationOf(parameter, input, computed, delivered))));
  }

  /**
   * 公式落点的依据 = 推导原文 ＋ 公式本身的出处，两半都要：推导说这个数怎么算的（没有它写作步会编一个，2026-08-31 立案），出处说这条公式凭什么（"行业通行做法 + 内部规范
   * §5.2 转写"——标注层印给业主看的那一句）。 推导写不出（未登记推导的资产）就只剩出处，与直取值落点同形态；出处为空就只剩推导，绝不编。
   */
  private static String provenanceOf(ParameterAsset parameter, String derivation) {
    String source = parameter.source();
    boolean hasSource = source != null && !source.isBlank();
    if (derivation == null) {
      return hasSource ? source : null;
    }
    return hasSource ? derivation + "公式依据：" + source : derivation;
  }

  /**
   * 这个数是怎么算出来的——**如实写，写不出就返回 null 由出处顶上，绝不编**。
   *
   * <p>射程只覆盖求值线自己实现了公式的那几条：它们的推导在代码里，代码知道就该说出来。取整了的要把取整前后都写出来 （"2136，取整到 10 mm ＝
   * 2140"）——推导原文进包，业主看到的输入与输出要对得上；不声明取整的没有这一句。
   */
  private static String derivationOf(
      ParameterAsset parameter, EvaluationInput input, Object computed, Object delivered) {
    String rounding = roundingClause(delivered, parameter.roundTo(), parameter.unit());
    return switch (parameter.assetId()) {
      case "lkp-counter-height" ->
          "求值线按公式算出：主厨身高 "
              + input.chiefHeightMm()
              + " mm ÷ 2 ＋ "
              + COUNTER_OFFSET_MIN
              + "–"
              + COUNTER_OFFSET_MAX
              + " mm ＝ "
              + text(computed)
              + rounding
              + "。";
      case "lkp-wardrobe-rod" ->
          "求值线按公式算出：身高 "
              + input.tallestHeightMm()
              + " mm × 1.2 ＝ "
              + text(computed)
              + rounding
              + "。";
      case "lkp-mirror-height" ->
          "求值线按公式算出：使用者眼高 " + input.eyeHeightMm() + " mm 直接取用" + rounding + "。";
      case "lkp-tv-distance" ->
          "求值线按公式算出：屏高 "
              + input.tvScreenHeightMm()
              + " mm × 3–4 ＝ "
              + text(computed)
              + rounding
              + "。";
      case "lkp-storage-total-meters" ->
          "求值线按公式算出：套内面积 "
              + input.netAreaSqm()
              + " ㎡（建筑面积 "
              + input.buildingAreaSqm()
              + " ㎡ × 得房率 "
              + input.floorAreaRatioPercent()
              + "%）× 收纳密度基准（米/㎡）＝ "
              + text(computed)
              + rounding
              + "。密度基准是经验条目、无外部源，故本条区间偏宽；"
              + "定稿平面接通后改按各柜体投影沿墙长度实算，区间随之收窄。";
      default -> null;
    };
  }

  /**
   * 推导原文里的取整那一句："，取整到 10 mm ＝ 2140"。未声明粒度（{@code granularity == null}）即空串——没取整就不说取整。
   *
   * <p>取整前后相等也照写：业主看到"＝ 900–950，取整到 10 mm ＝ 900–950"知道这个数过了取整这一步，比看到一个没说明的整数更准确。
   */
  private static String roundingClause(Object delivered, Double granularity, String unit) {
    if (granularity == null) {
      return "";
    }
    return "，取整到 " + text(granularity) + " " + unit + " ＝ " + text(delivered);
  }

  /** 推导原文里的数：标量原样，区间写成 "min–max"（单边界只写有的那一侧）。 */
  private static String text(Object value) {
    if (value instanceof Map<?, ?> map) {
      Object min = map.get("min");
      Object max = map.get("max");
      if (min != null && max != null) {
        return text(min) + "–" + text(max);
      }
      return min != null ? text(min) : text(max);
    }
    if (value instanceof Double d && d == Math.rint(d) && !d.isInfinite()) {
      return String.valueOf(d.longValue());
    }
    return String.valueOf(value);
  }

  /**
   * 按声明的粒度取整（规则 4.10e 增补）：标量取整，区间 {@code {min,max}} 两端各自取整；粒度为 {@code null} 即原样返回。
   *
   * <p>走 {@link BigDecimal} 不走 {@code Math.round(v / g) * g}：粒度 0.1 时后者会把 30.8 算成
   * 30.800000000000004—— 取整是为了去掉假精度，不能自己再造一截浮点尾巴。粒度是整数（10、100）时结果给整数，粒度带小数（0.1）时给对应位数的小数。
   */
  public static Object roundTo(Object value, Double granularity) {
    if (granularity == null) {
      return value;
    }
    if (value instanceof Number n) {
      return roundNumber(n, granularity);
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> rounded = new LinkedHashMap<>();
      map.forEach(
          (k, v) ->
              rounded.put(
                  String.valueOf(k), v instanceof Number n ? roundNumber(n, granularity) : v));
      return rounded;
    }
    return value;
  }

  private static Number roundNumber(Number value, double granularity) {
    BigDecimal step = BigDecimal.valueOf(granularity);
    BigDecimal rounded =
        BigDecimal.valueOf(value.doubleValue())
            .divide(step, 0, RoundingMode.HALF_UP)
            .multiply(step);
    if (step.stripTrailingZeros().scale() <= 0) {
      return rounded.longValueExact();
    }
    return rounded.setScale(step.stripTrailingZeros().scale(), RoundingMode.HALF_UP).doubleValue();
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
   * 正文，但同一句话里跟着一句**编的**解释——「这个范围锚定的是当前囤货节奏与墙面可嵌入家具形态 的交集」。查下去，这条算出来的落点 {@code source} 是
   * null：**我们没告诉写作步这个数怎么来的， 它就自己编了一个**。数字是真的、解释是假的，比两个都假更危险。
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
            .map(
                v -> {
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
    // 不在这里取整：一位小数是这条资产声明的粒度（round_to: 0.1），由 resolve 按声明统一取整——
    // 代码里再写死一份就是同一件事两处说（此前正是这么写的，2026-09-08 挪回数据）
    // 乘法走 BigDecimal：88 × 0.35 在 double 里是 30.800000000000004，那截尾巴会原样进推导原文
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("min", exactProduct(netArea, min));
    value.put("max", exactProduct(netArea, max));
    return value;
  }

  private static double exactProduct(double a, Number b) {
    return BigDecimal.valueOf(a).multiply(BigDecimal.valueOf(b.doubleValue())).doubleValue();
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
