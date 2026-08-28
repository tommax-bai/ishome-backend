package com.ishome.project.domain.rulebook;

import java.util.List;
import java.util.Map;

/**
 * 报告数据包（图 v0.2 §2）：求值线产物，成文线 input_snapshot 的内容本体——**自包含，成文线不回查任何库**： lkp- 落点对象 + gap- 缺口 + persona
 * 载荷（全文，非引用）+ cr- 判据 + 禁词表 + 匿名画像回显。 锁定清单与动作表随清单求值落地时加入。
 *
 * <p>确定性纪律：anchors/gaps 按 lkpId、releases 按 domain、各域内资产按 assetId 排序——同输入字节级同输出 （规则 8.2 可重放，图 v0.2
 * §8 首批验证项）。契约投影：contracts rulebook/report_data_package.schema.json。
 */
public record ReportDataPackage(
    List<String> domains,
    List<ReleaseRef> releases,
    List<ReportAnchor> anchors,
    List<GapRecord> gaps,
    Map<String, List<PersonaAsset>> personasByDomain,
    Map<String, List<CheckAsset>> checksByDomain,
    Map<String, List<String>> bannedTermsByDomain,
    EvaluationInput anonymousProfile) {}
