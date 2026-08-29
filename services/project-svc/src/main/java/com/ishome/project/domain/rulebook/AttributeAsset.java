package com.ishome.project.domain.rulebook;

import java.time.LocalDate;
import java.util.Map;

/**
 * attribute 形态资产（release 快照内投影）：实体 × 属性的参考数据（规则 4.12 五形态之一）。
 *
 * <p>{@code entityType} 决定 {@code props} 的结构（contracts {@code rulebook/attributes/*.schema.json} 一
 * entity_type 一 schema）。求值线当前只消费 {@code work_item}（工项单价，规则 5.15 造价章）—— material/color/storage_item
 * 是描述性属性，不是数字落点，投影它们只会造出一批没有值形态的落点。
 *
 * <p>{@code effectiveFrom/To} 是**表列不是 props 键**（props 内同名字段是导入镜像，见 work_item schema）：
 * 时效资产的治理头集中在列上，落点的 {@link AnchorProvenance} 从这里取——parameters 表没有这两列， 故"标注取数时间"这条纪律（规则
 * 4.10c/5.15）只有单价这条来源填得出真值。
 */
public record AttributeAsset(
    String assetId,
    String name,
    String entityType,
    Map<String, Object> props,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String calibration,
    String source,
    int version) {}
