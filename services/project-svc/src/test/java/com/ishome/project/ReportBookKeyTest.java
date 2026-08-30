package com.ishome.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ishome.project.domain.rulebook.ReportBookKey;
import org.junit.jupiter.api.Test;

/**
 * 册的对象键：**写的一侧与签的一侧之间的接头**。
 *
 * <p>唯一真源在 ishome-contracts {@code registries/object_keys.md}；写册那一侧（reportrender，Python）
 * 持另一份逐字副本，也有一条同样的守门测试。 两处对不上就是接不上头——签的一侧会对着一个不存在的键去问， 永远回"还没出册"，而册其实早就写好了。这种失效**不会报错**，所以必须由测试盯住。
 */
class ReportBookKeyTest {

  private static final String REPORT_ID = "01M18E1YGKVQZGCCNB0PCY4K7B";

  @Test
  void keyIsDerivedFromReportIdVerbatimPerContracts() {
    assertThat(ReportBookKey.of(REPORT_ID)).isEqualTo("reports/" + REPORT_ID + "/book.html");
  }

  /** 确定性派生 = 不必查台账就能算出同一个键，也 = 同一份报告重跑覆盖同一个对象。 */
  @Test
  void sameReportAlwaysYieldsSameKey() {
    assertThat(ReportBookKey.of(REPORT_ID)).isEqualTo(ReportBookKey.of(REPORT_ID));
  }

  /** 当不了键的一段就响亮失败，不悄悄改写它——带斜杠会把对象指到别处去。 */
  @Test
  void reportIdThatCannotBeAKeyFailsLoud() {
    assertThatThrownBy(() -> ReportBookKey.of("../../etc/passwd"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ReportBookKey.of("")).isInstanceOf(IllegalArgumentException.class);
  }
}
