package com.ishome.project.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishome.project.domain.port.ReleaseRepository;
import com.ishome.project.domain.rulebook.CheckAsset;
import com.ishome.project.domain.rulebook.ParameterAsset;
import com.ishome.project.domain.rulebook.PersonaAsset;
import com.ishome.project.domain.rulebook.ReleaseSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.stereotype.Repository;

/**
 * svc_rulebook.releases PG 实现：快照 jsonb → 求值线投影（parameters/personas/checks/禁词，其余形态随后续扩展）。
 * 解码失败视为发布物损坏直接抛出——release 是不可变契约数据，静默跳条目即静默假成功。
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
              node.path("version").asInt(1)));
    }
    return checks;
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
