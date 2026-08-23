package com.ishome.project.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.ulid.UlidCreator;
import com.ishome.project.domain.RevisionDirective;
import com.ishome.project.domain.RevisionLog;
import com.ishome.project.domain.port.RevisionLogRepository;
import org.springframework.stereotype.Repository;

/** svc_project.revision_log PG 实现（只增不改）：directive 序列化为 JSON 落 jsonb。 */
@Repository
public class RevisionLogRepositoryImpl implements RevisionLogRepository {

  private final RevisionLogMapper revisionLogMapper;
  private final ObjectMapper objectMapper;

  public RevisionLogRepositoryImpl(RevisionLogMapper revisionLogMapper, ObjectMapper objectMapper) {
    this.revisionLogMapper = revisionLogMapper;
    this.objectMapper = objectMapper;
  }

  @Override
  public void save(RevisionLog revisionLog) {
    RevisionLogPO po = new RevisionLogPO();
    po.setId(UlidCreator.getUlid().toString());
    po.setProjectId(revisionLog.projectId());
    po.setMilestone(revisionLog.milestone());
    po.setRoundNo(revisionLog.roundNo());
    po.setDirective(toJson(revisionLog.directive()));
    po.setTaskId(revisionLog.taskId());
    revisionLogMapper.insert(po);
  }

  @Override
  public int countByProjectIdAndMilestone(String projectId, String milestone) {
    return revisionLogMapper.countActiveByProjectIdAndMilestone(projectId, milestone);
  }

  private String toJson(RevisionDirective directive) {
    try {
      return objectMapper.writeValueAsString(directive);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("结构化修订指令序列化失败", e);
    }
  }
}
