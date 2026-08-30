package com.ishome.project.domain.rulebook;

/**
 * 触发依据（规则 4.3 可追溯性的户型侧对应物）：这条规则**为什么**对这一户成立。
 *
 * <p>{@code type} = 触发类型词面，随 {@link RuleAsset#trigger()} 的 {@code type} 逐字下发； {@code feature} =
 * 户型特征标记名（取值闭集见 contracts {@code rulebook/layout_features.json}）； {@code evidence} =
 * **这条标记成立的依据**（人话，来自解析产出的特征值），报告里"因为你家阳台带家政位" 的数据来源就是它。
 *
 * <p>{@code always} 触发时后两者为 {@code null}——无条件成立的规则没有"因为"，编一个就是伪因果（规范 §12）。
 */
public record TriggerEvidence(String type, String feature, String evidence) {}
