package com.ishome.project.infrastructure.definition;

import com.ishome.project.domain.definition.ArtifactCriterion;
import com.ishome.project.domain.definition.CompletionCriteria;
import com.ishome.project.domain.definition.MilestoneDefinition;
import com.ishome.project.domain.definition.OnEnterAction;
import com.ishome.project.domain.definition.ProcessDefinition;
import com.ishome.project.domain.definition.RevisionRule;
import com.ishome.project.domain.definition.SlotCriterion;
import com.ishome.project.domain.definition.SlotDefinition;
import com.ishome.project.domain.port.ProcessDefinitionRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 内置流程定义（v1）——里程碑 M0-M6 按《沟通助手架构方案》§7 填充为数据。
 *
 * <p>定义是版本化配置制品：归业务域所有、随 monorepo 走评审；后续演进为配置文件装载， 版本只增不改（存量项目 process_version
 * 已固化）。判据全部是简单谓词（红线：不发明表达式语法）； M5 "覆盖已确认空间"超出简单谓词表达力，v1 以"明细清单确认"为代理判据， 真逻辑届时做成 project-svc 服务接口。
 */
@Repository
public class ProcessDefinitionRepositoryImpl implements ProcessDefinitionRepository {

  /** 产物类型 key（数据值；delivery_set 对齐 contracts glossary "交付图集"）。 */
  static final String ARTIFACT_VISION_IMAGE = "vision_image";

  static final String ARTIFACT_LAYOUT_PLAN = "layout_plan";
  static final String ARTIFACT_SPACE_RENDER = "space_render";
  static final String ARTIFACT_CAD_DRAFT = "cad_draft";
  static final String ARTIFACT_DETAIL_LIST = "detail_list";
  static final String ARTIFACT_DELIVERY_SET = "delivery_set";

  private final Map<String, ProcessDefinition> definitions = Map.of("v1", buildV1Definition());

  @Override
  public Optional<ProcessDefinition> findByVersion(String version) {
    return Optional.ofNullable(definitions.get(version));
  }

  private static ProcessDefinition buildV1Definition() {
    return new ProcessDefinition(
        "v1",
        List.of(
            milestoneM0(),
            milestoneM05(),
            milestoneM1(),
            milestoneM2(),
            milestoneM3(),
            milestoneM4(),
            milestoneM5(),
            milestoneM6()));
  }

  /** M0 建档：户型图已传 + 面积/城市/预算区间/家庭结构槽位齐（任一认知状态，已填即可）。 */
  private static MilestoneDefinition milestoneM0() {
    return new MilestoneDefinition(
        "M0",
        "建档",
        null,
        List.of(
            new SlotDefinition("floorplan", "floorplan_ref", "户型图：户型库命中（优先）或用户上传解析（兜底）", List.of()),
            new SlotDefinition("usable_area_sqm", "number", "使用面积，平方米", List.of()),
            new SlotDefinition("city", "text", "所在城市", List.of()),
            new SlotDefinition("budget_range_cents", "range", "装修预算区间，金额以分计", List.of()),
            new SlotDefinition("family_structure", "text", "家庭结构（几口人/有无老人小孩宠物）", List.of())),
        List.of("ask_to_collect", "explain", "clarify", "soothe_slow_down"),
        new CompletionCriteria(
            List.of(
                SlotCriterion.filled("floorplan"),
                SlotCriterion.filled("usable_area_sqm"),
                SlotCriterion.filled("city"),
                SlotCriterion.filled("budget_range_cents"),
                SlotCriterion.filled("family_structure")),
            List.of()),
        List.of(),
        null);
  }

  /** M0.5 愿景图：生成并送达即完成（不求准求心动，无需确认）——情绪价值前置（D9）。 */
  private static MilestoneDefinition milestoneM05() {
    return new MilestoneDefinition(
        "M0.5",
        "愿景图",
        ARTIFACT_VISION_IMAGE,
        List.of(),
        List.of("present_artifact", "explain", "soothe_slow_down"),
        new CompletionCriteria(
            List.of(), List.of(ArtifactCriterion.presented(ARTIFACT_VISION_IMAGE))),
        List.of(OnEnterAction.createTask(ARTIFACT_VISION_IMAGE)),
        null);
  }

  /** M1 需求画像：风格方向/功能需求/硬性约束确认（USER_CONFIRMED，确认闭环授予）。 */
  private static MilestoneDefinition milestoneM1() {
    return new MilestoneDefinition(
        "M1",
        "需求画像",
        null,
        List.of(
            new SlotDefinition(
                "style_direction", "enum", "用户对整体感觉/风格/喜好的表述", List.of("原木", "奶油", "极简", "复古")),
            new SlotDefinition("functional_requirements", "multi", "功能需求（收纳/办公/儿童活动等）", List.of()),
            new SlotDefinition("hard_constraints", "multi", "硬性约束（不动墙/保留家具/预算上限等）", List.of())),
        List.of(
            "ask_to_collect",
            "explain",
            "present_artifact",
            "clarify",
            "confirm_milestone_advance",
            "soothe_slow_down"),
        new CompletionCriteria(
            List.of(
                SlotCriterion.userConfirmed("style_direction"),
                SlotCriterion.userConfirmed("functional_requirements"),
                SlotCriterion.userConfirmed("hard_constraints")),
            List.of()),
        List.of(),
        null);
  }

  /** M2 平面布局：布置方案图用户确认；修订循环维度词表 + 软预算 3 轮。 */
  private static MilestoneDefinition milestoneM2() {
    return new MilestoneDefinition(
        "M2",
        "平面布局",
        ARTIFACT_LAYOUT_PLAN,
        List.of(),
        List.of(
            "present_artifact",
            "map_feedback",
            "clarify",
            "confirm_milestone_advance",
            "soothe_slow_down"),
        new CompletionCriteria(
            List.of(), List.of(ArtifactCriterion.confirmed(ARTIFACT_LAYOUT_PLAN))),
        List.of(OnEnterAction.createTask(ARTIFACT_LAYOUT_PLAN)),
        new RevisionRule(List.of("layout_density", "circulation", "zoning", "furniture_scale"), 3));
  }

  /** M3 效果呈现：主要空间渲染图认可（确认）；修订维度为效果向词表。 */
  private static MilestoneDefinition milestoneM3() {
    return new MilestoneDefinition(
        "M3",
        "效果呈现",
        ARTIFACT_SPACE_RENDER,
        List.of(),
        List.of(
            "present_artifact",
            "map_feedback",
            "clarify",
            "confirm_milestone_advance",
            "soothe_slow_down"),
        new CompletionCriteria(
            List.of(), List.of(ArtifactCriterion.confirmed(ARTIFACT_SPACE_RENDER))),
        List.of(OnEnterAction.createTask(ARTIFACT_SPACE_RENDER)),
        new RevisionRule(List.of("color_tone", "material", "lighting", "style_intensity"), 3));
  }

  /** M4 CAD 初稿（参考级）：生成 + 确认。 */
  private static MilestoneDefinition milestoneM4() {
    return new MilestoneDefinition(
        "M4",
        "CAD 初稿",
        ARTIFACT_CAD_DRAFT,
        List.of(),
        List.of("present_artifact", "explain", "confirm_milestone_advance", "soothe_slow_down"),
        new CompletionCriteria(List.of(), List.of(ArtifactCriterion.confirmed(ARTIFACT_CAD_DRAFT))),
        List.of(OnEnterAction.createTask(ARTIFACT_CAD_DRAFT)),
        null);
  }

  /** M5 明细：家具/材质/尺寸清单。"覆盖已确认空间"以清单确认为 v1 代理判据（见类注释）。 */
  private static MilestoneDefinition milestoneM5() {
    return new MilestoneDefinition(
        "M5",
        "明细",
        ARTIFACT_DETAIL_LIST,
        List.of(),
        List.of("present_artifact", "explain", "confirm_milestone_advance", "soothe_slow_down"),
        new CompletionCriteria(
            List.of(), List.of(ArtifactCriterion.confirmed(ARTIFACT_DETAIL_LIST))),
        List.of(OnEnterAction.createTask(ARTIFACT_DETAIL_LIST)),
        null);
  }

  /** M6 交付：全套打包。终点里程碑，判据为空 = 不自动完成（对齐 §7 判据 "—"）。 */
  private static MilestoneDefinition milestoneM6() {
    return new MilestoneDefinition(
        "M6",
        "交付",
        ARTIFACT_DELIVERY_SET,
        List.of(),
        List.of("present_artifact", "explain", "soothe_slow_down"),
        CompletionCriteria.none(),
        List.of(OnEnterAction.createTask(ARTIFACT_DELIVERY_SET)),
        null);
  }
}
