package com.ishome.project.domain.rulebook;

/**
 * 语域判据（规则 4.10a/5.8 在消费侧的实装）：把 calibration 标记变成强制的呈现档位。
 *
 * <p>判据一条：**过可核性门的可作判断句支点，没过的语域限建议口吻**。calibrated 是断言预算唯一合法的
 * 背书来源；未过门的落点照常进正文、照常进主旨句，只是不能拿来当"国标要求"的支点——它说的是 "我们建议"。
 *
 * <p><b>v2.4 裁决 2026-08-29 拆掉了本类的另外三条判据</b>（记此供审计，规范 §14.9）：原判据在 PAID 侧还判
 * "隐藏"——定位数字一律隐藏、值里找不到区间的一律隐藏、判不准的一律隐藏，产物权益档与值形态都参与判定。
 * 隐藏这一档整体作废：它同时拿掉了业主的价值（藏起来的建议等于没有）与系统的信号（行为信号全部来自 业主对内容的反应），规则 4.10a
 * 的转正路径在冷启动期因此成了死循环。要防的风险不是"业主看到没依据的数"， 是"业主**误以为**这个数有依据"——那由 {@link AnchorProvenancePolicy}
 * 的标注承接，成本低得多。 故本类不再看 {@link ArtifactEntitlement}，也不再看值的形态。
 *
 * <p>纯函数、无 IO、不认识任何 {@code art-} 产物。
 */
public final class AnchorPresentationPolicy {

  private static final String CALIBRATION_CALIBRATED = "calibrated";

  public AnchorPresentation decide(String calibration) {
    return CALIBRATION_CALIBRATED.equals(calibration)
        ? AnchorPresentation.THESIS_SUPPORT
        : AnchorPresentation.REFERENCE_ONLY;
  }
}
