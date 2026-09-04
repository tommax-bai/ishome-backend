package com.ishome.project.infrastructure.persistence;

import com.ishome.project.domain.GenerationTask;
import com.ishome.project.domain.GenerationTaskStatus;
import com.ishome.project.domain.port.GenerationTaskRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** svc_project.generation_tasks PG 实现：save 为按 id 的 insert-or-update（完成后回填 artifact_id）。 */
@Repository
public class GenerationTaskRepositoryImpl implements GenerationTaskRepository {

  private final GenerationTaskMapper generationTaskMapper;

  public GenerationTaskRepositoryImpl(GenerationTaskMapper generationTaskMapper) {
    this.generationTaskMapper = generationTaskMapper;
  }

  @Override
  public void save(GenerationTask task) {
    GenerationTaskPO po = toPo(task);
    if (generationTaskMapper.selectById(task.id()) == null) {
      generationTaskMapper.insert(po);
    } else {
      po.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
      generationTaskMapper.updateById(po);
    }
  }

  @Override
  public Optional<GenerationTask> findById(String taskId) {
    return Optional.ofNullable(generationTaskMapper.findActiveById(taskId)).map(this::toDomain);
  }

  @Override
  public List<GenerationTask> listByProjectId(String projectId) {
    return generationTaskMapper.listActiveByProjectId(projectId).stream()
        .map(this::toDomain)
        .toList();
  }

  private GenerationTaskPO toPo(GenerationTask task) {
    GenerationTaskPO po = new GenerationTaskPO();
    po.setId(task.id());
    po.setProjectId(task.projectId());
    po.setTaskType(task.taskType());
    po.setInputSnapshot(task.inputSnapshot());
    po.setStatus(task.status().name());
    po.setArtifactId(task.artifactId());
    po.setResult(task.result());
    return po;
  }

  private GenerationTask toDomain(GenerationTaskPO po) {
    return new GenerationTask(
        po.getId(),
        po.getProjectId(),
        po.getTaskType(),
        po.getInputSnapshot(),
        GenerationTaskStatus.valueOf(po.getStatus()),
        po.getArtifactId(),
        po.getResult());
  }
}
