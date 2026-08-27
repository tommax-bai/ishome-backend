package com.ishome.project.domain.rulebook;

import java.util.List;
import java.util.Map;

/**
 * 报告数据包（图 v0.2 §2）：求值线产物，成文线 input_snapshot 的内容本体——自包含，成文线不回查任何库。
 *
 * <p>首实装范围：lkp- 落点对象 + gap- 缺口 + persona release 引用 + 匿名画像回显；锁定清单与动作表 随清单求值落地时加入。确定性纪律：anchors/gaps
 * 按 lkpId、releases 按 domain 排序——同输入字节级同输出 （规则 8.2 可重放，图 v0.2 §8 首批验证项）。
 */
public record ReportDataPackage(
    List<String> domains,
    List<ReleaseRef> releases,
    List<ReportAnchor> anchors,
    List<GapRecord> gaps,
    Map<String, List<PersonaAssetRef>> personasByDomain,
    EvaluationInput anonymousProfile) {}
