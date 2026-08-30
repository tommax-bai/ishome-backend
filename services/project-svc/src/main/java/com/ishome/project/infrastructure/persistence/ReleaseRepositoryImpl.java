package com.ishome.project.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishome.project.domain.port.ReleaseRepository;
import com.ishome.project.domain.rulebook.AttributeAsset;
import com.ishome.project.domain.rulebook.CheckAsset;
import com.ishome.project.domain.rulebook.CheckExample;
import com.ishome.project.domain.rulebook.ParameterAsset;
import com.ishome.project.domain.rulebook.PersonaAsset;
import com.ishome.project.domain.rulebook.ReleaseSnapshot;
import com.ishome.project.domain.rulebook.RuleAsset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.stereotype.Repository;

/**
 * svc_rulebook.releases PG 实现：快照 jsonb → 求值线投影（parameters/attributes/rules/personas/checks/禁词，
 * templates 随句式拼装落地时扩展）。 解码失败视为发布物损坏直接抛出——release 是不可变契约数据，静默跳条目即静默假成功。
 */
@Repository
public class ReleaseRepositoryImpl implements ReleaseRepository {

  private final ReleaseMapper releaseMapper;
  private final ObjectMapper objectMapper;

  public ReleaseRepositoryImpl(ReleaseMapper releaseMapper, ObjectMapper objectMapper) {
    this.releaseMapper = releaseMapper;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<ReleaseSnapshot> findLatest(String domain) {
    ReleasePO po = releaseMapper.findLatestByDomain(domain);
    return Optional.ofNullable(po).map(this::toDomain);
  }

  private ReleaseSnapshot toDomain(ReleasePO po) {
    try {
      JsonNode assets = objectMapper.readTree(po.getSnapshot()).path("assets");
      return new ReleaseSnapshot(
          po.getDomain(),
          po.getReleaseTag(),
          parameters(assets),
          attributes(assets),
          rules(assets),
          personas(assets),
          checks(assets),
          bannedTerms(assets));
    } catch (Exception e) {
      throw new IllegalStateException("release 快照解码失败：" + po.getReleaseTag(), e);
    }
  }

  private List<ParameterAsset> parameters(JsonNode assets) {
    List<ParameterAsset> parameters = new ArrayList<>();
    for (JsonNode node : assets.path("parameters")) {
      parameters.add(
          new ParameterAsset(
              node.path("asset_id").asText(),
              node.path("name").asText(),
              node.path("number_class").asText(null),
              node.path("value").isObject() ? toMap(node.path("value")) : null,
              node.path("formula").asText(null),
              node.path("unit").asText(null),
              node.path("calibration").asText("draft"),
              node.path("source").asText(null),
              node.path("version").asInt(1)));
    }
    return parameters;
  }

  /**
   * attribute 形态投影：治理头（calibration/source/effective_*）取**表列**，props 原样带走。
   *
   * <p>props 内的 {@code effective_from/to} 是导入镜像（contracts work_item schema 明写"以表列为准"），
   * 此处不读——两处不一致时读镜像等于让快照自己说了不算。缺列即 {@code null}：无时效的属性（材质卡、 色板卡）本就没有取数时间，编一个反而给标注层造出假的"有效期"。
   */
  private List<AttributeAsset> attributes(JsonNode assets) {
    List<AttributeAsset> attributes = new ArrayList<>();
    for (JsonNode node : assets.path("attributes")) {
      attributes.add(
          new AttributeAsset(
              node.path("asset_id").asText(),
              node.path("name").asText(),
              node.path("entity_type").asText(null),
              node.path("props").isObject() ? toMap(node.path("props")) : Map.of(),
              date(node.path("effective_from")),
              date(node.path("effective_to")),
              node.path("calibration").asText("draft"),
              node.path("source").asText(null),
              node.path("version").asInt(1)));
    }
    return attributes;
  }

  /**
   * rule 形态投影（规范 §4.1 三层三触发）：{@code trigger} 原样带走，判定交 {@code RuleTriggerPolicy}。
   *
   * <p>不在此处按触发类型过滤：投影层只负责把快照说的话搬全，"哪条对这一户成立"是判据的事—— 在这里筛就等于把触发语义拆成两处，加一个触发类型要改两个文件。{@code trigger}
   * 缺失 → 空 map（判据据此判为未触发），不猜一个默认类型：猜出来的触发条件会让规则**无条件**进每一份包。
   */
  private List<RuleAsset> rules(JsonNode assets) {
    List<RuleAsset> rules = new ArrayList<>();
    for (JsonNode node : assets.path("rules")) {
      rules.add(
          new RuleAsset(
              node.path("asset_id").asText(),
              node.path("layer").asText(null),
              node.path("content").asText(""),
              node.path("rationale").asText(null),
              node.path("severity").asText(null),
              node.path("calibration").asText("draft"),
              node.path("trigger").isObject() ? toMap(node.path("trigger")) : Map.of(),
              toStringList(node.path("consumers"))));
    }
    return rules;
  }

  /** 快照内日期为 ISO 文本（to_jsonb 的 date 形态）；缺失/空串 → null，不猜。 */
  private static LocalDate date(JsonNode node) {
    String text = node.asText(null);
    return text == null || text.isBlank() ? null : LocalDate.parse(text);
  }

  private List<PersonaAsset> personas(JsonNode assets) {
    List<PersonaAsset> personas = new ArrayList<>();
    for (JsonNode node : assets.path("personas")) {
      personas.add(
          new PersonaAsset(
              node.path("asset_id").asText(),
              node.path("identity").asText(""),
              toList(node.path("judgment_samples")),
              toList(node.path("assertion_budget")),
              node.path("banned_terms").isObject() ? toMap(node.path("banned_terms")) : Map.of(),
              node.path("version").asInt(1)));
    }
    return personas;
  }

  private List<CheckAsset> checks(JsonNode assets) {
    List<CheckAsset> checks = new ArrayList<>();
    for (JsonNode node : assets.path("checks")) {
      checks.add(
          new CheckAsset(
              node.path("asset_id").asText(),
              node.path("check_type").asText(),
              toStringList(node.path("scope")),
              node.path("pattern").asText(null),
              node.path("requirement").asText(null),
              node.path("message").asText(""),
              node.path("decided_by").asText(""),
              toStringList(node.path("threshold_refs")),
              examples(node.path("examples")),
              // V4 之前发布的 release 快照没有 status 列：缺省读作 observing——判据的拦截权只能被
              // 显式授予（规则 4.17 门禁二），老快照默认无拦截权是安全方向（少拦不多拦）
              node.path("status").asText("observing"),
              node.path("version").asInt(1)));
    }
    return checks;
  }

  /** 判官反例样例：三件不齐的条目直接丢弃——半条样例教不出判据，留着只会让判官学歪（规则 4.17 种子集真实度=系统天花板）。 */
  private List<CheckExample> examples(JsonNode node) {
    List<CheckExample> examples = new ArrayList<>();
    for (JsonNode item : node) {
      String bad = item.path("bad").asText("");
      String why = item.path("why").asText("");
      String fixed = item.path("fixed").asText("");
      if (!bad.isBlank() && !why.isBlank() && !fixed.isBlank()) {
        examples.add(new CheckExample(bad, why, fixed));
      }
    }
    return List.copyOf(examples);
  }

  /** vocabulary(kind=banned_term) 的 terms（类别 → 词列表）平铺去重排序——公共禁词已在发布时物化进本域快照。 */
  private List<String> bannedTerms(JsonNode assets) {
    TreeSet<String> terms = new TreeSet<>();
    for (JsonNode node : assets.path("vocabularies")) {
      if (!"banned_term".equals(node.path("kind").asText())) {
        continue;
      }
      node.path("terms")
          .properties()
          .forEach(entry -> entry.getValue().forEach(term -> terms.add(term.asText())));
    }
    return List.copyOf(terms);
  }

  private Map<String, Object> toMap(JsonNode node) {
    return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
  }

  private List<Object> toList(JsonNode node) {
    if (!node.isArray()) {
      return List.of();
    }
    return objectMapper.convertValue(node, new TypeReference<List<Object>>() {});
  }

  private List<String> toStringList(JsonNode node) {
    if (!node.isArray()) {
      return List.of();
    }
    return objectMapper.convertValue(node, new TypeReference<List<String>>() {});
  }
}
