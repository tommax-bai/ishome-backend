package com.ishome.project.domain.rulebook;

/**
 * 落点对象（图 v0.2 §2 报告数据包成员）：一个 lkp- 的求值结果。成文线的数字字段只能引用本对象， 机检可逐字段比对零漂移（图 v0.2 §3）。
 *
 * <p><b>两层模型（规则 1.9，规范 v2.8）</b>：一条落点 = 若干「项」，一项的值 = 一个数，或一个区间。 正文可以引用其中一项，写作 {@code
 * {lkp-x.项名}}。{@code valueKind} 判定三件事，**都不靠推断**： ①{@code value} 的形态；②可否单项引用（{@code single}/{@code
 * range} 只有一个匿名项， 只能整条引用）；③项名受哪套约束（闭集 / 形态受控 / 受控词表）。
 *
 * <p>{@code value} 因此是 {@code Object} 而非 Map：{@code single} 是标量，{@code range} 是 {@code {min,max}}，
 * 其余五类是 {@code 项名 → 标量|{min,max}}。**{@code min}/{@code max} 不是项，是项的值形态**——故 {@code {lkp-x.min}}
 * 在语法上不存在，"引一端丢掉另一端"由结构堵死而非纪律禁止（物理隔离优先于规则隔离）。
 *
 * <p>{@code unit} 与 {@code referencePlane} 是**元信息，各有各的字段**（规则 1.9 二）：只要它们与项同层， "{@code
 * {lkp-x.unit}} 引用出一个单位字符串"就是语法上合法的写法，靠约定管不住。
 *
 * <p>{@code value} 为求值后的数值包（直取参数时=快照原值，公式时=代入匿名输入的计算结果）；{@code basisTag} + {@code source} =
 * 依据（release 引用 + 推导可见的出处）。
 *
 * <p>三字段的分工（消费侧门禁）：{@code degraded} 是**标记**——未过可核性门（{@code calibration != calibrated}）； {@code
 * presentation} 是**语域强制**——过不过可核性门决定能不能作判断句支点（{@link AnchorPresentationPolicy}，规则 4.10a/5.8）；
 * {@code provenance} 是**标注强制**——未过门或已过期的落点进正文时同页必须挂依据标注（{@link AnchorProvenancePolicy}，规则 4.10c）。
 * 成文线按后两者执行，不按 {@code degraded} 自由裁量。
 *
 * <p>{@code source} 与 {@code calibration} 两个平铺字段是 v2.4 之前的形态，**权威载体已是 {@code provenance}**（同值）；
 * 契约"只增不删"故保留，新消费方读 {@code provenance}。 管的时刻/生活翻译两字段待资产回路补齐后加入（当前种子无此数据，不预造）。
 */
public record ReportAnchor(
    String lkpId,
    String name,
    String numberClass,
    String unit,
    String valueKind,
    Object value,
    String referencePlane,
    String basisTag,
    String source,
    String calibration,
    boolean degraded,
    AnchorProvenance provenance,
    AnchorPresentation presentation) {}
