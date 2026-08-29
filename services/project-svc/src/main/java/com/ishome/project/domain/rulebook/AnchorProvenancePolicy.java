package com.ishome.project.domain.rulebook;

import java.time.LocalDate;

/**
 * 标注判据（规则 4.10c，v2.4 裁决 2026-08-29）：判"这条落点进正文时要不要随页挂依据标注"。
 *
 * <p>两条判据，都是**确定性、机检可判**的——这正是它能替代隐藏档的原因（隐藏依赖"判不准就藏"这种 无法机检的兜底判据，v2.3 的三条隐藏判据随裁决一并作废）：
 *
 * <ol>
 *   <li><b>未过可核性门</b>（{@code calibration != calibrated}）：没有外部依据背书的数，业主有权知道 它有多硬；
 *   <li><b>时效越界</b>（{@code effectiveTo} 早于本次求值基准日）：过期数据照常下发，标注取数时间—— 抹掉反而少给业主一个判断维度。
 * </ol>
 *
 * <p><b>基准日是入参不是时钟</b>：{@code evaluatedOn} 由调用方给定并随包下发（{@link ReportDataPackage#evaluatedOn()}），
 * 求值线不读运行时时钟——否则同一份 release 同一份输入在两天里会算出两个包，规则 8.2 的字节级可重放当场失效。
 *
 * <p>判定在**生产侧**做完、结果随包下发（与 {@link AnchorPresentationPolicy} 同机制）：成文线只执行不重判。 纯函数、无 IO。
 */
public final class AnchorProvenancePolicy {

  private static final String CALIBRATION_CALIBRATED = "calibrated";

  public AnchorProvenance decide(
      String source,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String calibration,
      LocalDate evaluatedOn) {
    boolean unbacked = !CALIBRATION_CALIBRATED.equals(calibration);
    boolean stale = effectiveTo != null && effectiveTo.isBefore(evaluatedOn);
    return new AnchorProvenance(source, effectiveFrom, effectiveTo, calibration, unbacked || stale);
  }
}
