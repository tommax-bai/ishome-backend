package com.ishome.project.infrastructure.persistence;

import com.ishome.project.domain.Decision;
import com.ishome.project.domain.DecisionType;
import com.ishome.project.domain.port.DecisionRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** svc_project.decisions PG 实现（只增不改）。 */
@Repository
public class DecisionRepositoryImpl implements DecisionRepository {

  private final DecisionMapper decisionMapper;

  public DecisionRepositoryImpl(DecisionMapper decisionMapper) {
    this.decisionMapper = decisionMapper;
  }

  @Override
  public void save(Decision decision) {
    DecisionPO po = new DecisionPO();
    po.setId(decision.id());
    po.setProjectId(decision.projectId());
    po.setDecisionType(decision.decisionType().name());
    po.setMilestone(decision.milestone());
    po.setArtifactId(decision.artifactId());
    po.setSourceEventId(decision.sourceEventId());
    decisionMapper.insert(po);
  }

  @Override
  public List<Decision> listByProjectId(String projectId) {
    return decisionMapper.listActiveByProjectId(projectId).stream().map(this::toDomain).toList();
  }

  private Decision toDomain(DecisionPO po) {
    return new Decision(
        po.getId(),
        po.getProjectId(),
        DecisionType.valueOf(po.getDecisionType()),
        po.getMilestone(),
        po.getArtifactId(),
        po.getSourceEventId());
  }
}
