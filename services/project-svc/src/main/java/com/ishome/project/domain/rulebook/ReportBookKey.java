package com.ishome.project.domain.rulebook;

/**
 * 报告册在私有对象存储里的键——**由 report_id 确定性推得，不是分配的**。
 *
 * <p>唯一真源：ishome-contracts {@code registries/object_keys.md}（只增不改）；本类的模板串是它的**逐字副本**。
 * 写册的一侧（reportrender，Python）持另一份逐字副本，两侧各有一条守门测试盯住。 两个仓两种语言谁也不能 import
 * 谁，只能靠同一条键接头——对不上就是接不上头，不是风格问题。
 *
 * <p>确定性派生带来一个直接后果：**"这份报告出没出册"问存储即知，不必另立台账**。 台账会与真相漂移（写成功了没记上、记上了其实没写成），派生不会。
 */
public final class ReportBookKey {

  /** contracts {@code registries/object_keys.md} 的逐字副本。 */
  private static final String TEMPLATE = "reports/%s/book.html";

  private ReportBookKey() {}

  /**
   * 册的对象键。
   *
   * @throws IllegalArgumentException report_id 当不了键的一段（空、或带斜杠会把对象指到别处去）—— 响亮失败，不悄悄改写它
   */
  public static String of(String reportId) {
    if (reportId == null || reportId.isBlank() || reportId.contains("/")) {
      throw new IllegalArgumentException("report_id 当不了对象键的一段：" + reportId);
    }
    return TEMPLATE.formatted(reportId);
  }
}
