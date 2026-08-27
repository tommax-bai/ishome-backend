package com.ishome.project.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishome.project.domain.port.ReleaseRepository;
import com.ishome.project.domain.rulebook.ParameterAsset;
import com.ishome.project.domain.rulebook.PersonaAssetRef;
import com.ishome.project.domain.rulebook.ReleaseSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * svc_rulebook.releases PG 实现：快照 jsonb → 求值线投影（parameters/personas，其余形态随后续扩展）。
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
      List<ParameterAsset> parameters = new ArrayList<>();
      for (JsonNode node : assets.path("parameters")) {
        parameters.add(
            new ParameterAsset(
                node.path("asset_id").asText(),
                node.path("name").asText(),
                node.path("number_class").asText(null),
                node.path("value").isObject()
                    ? objectMapper.convertValue(
                        node.path("value"), new TypeReference<Map<String, Object>>() {})
                    : null,
                node.path("formula").asText(null),
                node.path("unit").asText(null),
                node.path("calibration").asText("draft"),
                node.path("source").asText(null),
                node.path("version").asInt(1)));
      }
      List<PersonaAssetRef> personas = new ArrayList<>();
      for (JsonNode node : assets.path("personas")) {
        personas.add(
            new PersonaAssetRef(node.path("asset_id").asText(), node.path("version").asInt(1)));
      }
      return new ReleaseSnapshot(po.getDomain(), po.getReleaseTag(), parameters, personas);
    } catch (Exception e) {
      throw new IllegalStateException("release 快照解码失败：" + po.getReleaseTag(), e);
    }
  }
}
