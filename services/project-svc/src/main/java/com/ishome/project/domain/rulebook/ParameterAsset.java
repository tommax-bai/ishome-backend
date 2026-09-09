package com.ishome.project.domain.rulebook;

/**
 * parameter 形态资产（release 快照内投影）：lkp- 求值落点的定义。
 *
 * <p>{@code valueKind} 是**两层模型**（规则 1.9，规范 v2.8）的类别声明，随快照原样带到落点上——
 * 一条落点由哪种项构成、可否单项引用、项名受哪套约束，三件事都由它判定，求值线**不从 value 的键名反推**。 它可以为 {@code
 * null}：公式落点在可执行形态登记前不产出落点，形态待定，此时硬填一个类别比缺席更坏。
 *
 * <p>{@code value} 是 {@code Object} 而非 Map（快照 jsonb 原样）：{@code single} 是标量、{@code range} 是 {@code
 * {min,max}}、其余五类是 {@code 项名 → 标量|{min,max}}。{@code referencePlane} 与 {@code unit} 是元信息、
 * 各有各的字段——v2.8 前参考平面挤在 {@code value} 里与项同层（规则 1.9 二）。
 *
 * <p>{@code formula} 为公式的文本形态——可执行形态在 {@link RulebookEvaluator} 按 assetId 显式实现 （数字不由 LLM 决定，图 v0.2
 * §0）。
 *
 * <p>{@code roundTo} 是**公式点值的取整粒度**（规则 4.10e 增补，用户裁决 2026-09-08），随 {@code unit} 计（尺寸类 10 mm、 长度 0.1
 * 米）。它是数据逐条声明的，求值线只照声明取整、区间两端各自取整；**{@code null} 即不取整**——用户原值、计数、规范原值
 * 都不声明，直取值落点上根本没有这个字段（核验拦）。渲染层一字不动（渲染层禁换算红线不变）。
 *
 * <p>{@code status} 是**入册状态**（V9 起，{@code active|retired}；用户裁决 2026-09-09「占比只由算得」）：{@code retired}
 * 的条目仍在快照里（release 不可变、旧值留档作对照），但求值线**不产落点、也不记 gap-**——它不是求不出，是已裁定不再给。 V9 之前的快照没有这一列，读作 {@link
 * #STATUS_ACTIVE}。
 */
public record ParameterAsset(
    String assetId,
    String name,
    String numberClass,
    String valueKind,
    Object value,
    String referencePlane,
    String formula,
    Double roundTo,
    String unit,
    String calibration,
    String source,
    int version,
    String status) {

  public static final String STATUS_ACTIVE = "active";

  public static final String STATUS_RETIRED = "retired";

  /** 在册条目（V9 之前的快照、以及没写 status 的种子，都是这一档）。 */
  public ParameterAsset(
      String assetId,
      String name,
      String numberClass,
      String valueKind,
      Object value,
      String referencePlane,
      String formula,
      Double roundTo,
      String unit,
      String calibration,
      String source,
      int version) {
    this(
        assetId,
        name,
        numberClass,
        valueKind,
        value,
        referencePlane,
        formula,
        roundTo,
        unit,
        calibration,
        source,
        version,
        STATUS_ACTIVE);
  }

  /** 未声明取整粒度的资产——直取值落点与不取整的公式落点的常态（不声明即不取整）。 */
  public ParameterAsset(
      String assetId,
      String name,
      String numberClass,
      String valueKind,
      Object value,
      String referencePlane,
      String formula,
      String unit,
      String calibration,
      String source,
      int version) {
    this(
        assetId,
        name,
        numberClass,
        valueKind,
        value,
        referencePlane,
        formula,
        null,
        unit,
        calibration,
        source,
        version);
  }

  /** 已退役：裁定不再下发的条目，求值线整条跳过。 */
  public boolean retired() {
    return STATUS_RETIRED.equals(status);
  }
}
