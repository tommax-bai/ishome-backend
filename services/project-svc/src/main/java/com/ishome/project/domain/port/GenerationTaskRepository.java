package com.ishome.project.domain.port;

import com.ishome.project.domain.GenerationTask;
import com.ishome.project.domain.GenerationTaskNotFoundException;
import java.util.List;
import java.util.Optional;

/** 生成任务业务真相仓储 port（执行语义在 Temporal，此处只落业务事实）。 */
public interface GenerationTaskRepository {

  void save(GenerationTask task);

  Optional<GenerationTask> findById(String taskId);

  default GenerationTask getById(String taskId) {
    return findById(taskId).orElseThrow(() -> new GenerationTaskNotFoundException(taskId));
  }

  List<GenerationTask> listByProjectId(String projectId);
}
