package com.ishome.project.domain.port;

import com.ishome.project.domain.GenerationTask;
import java.util.List;

/** 生成任务业务真相仓储 port（执行语义在 Temporal，此处只落业务事实）。 */
public interface GenerationTaskRepository {

  void save(GenerationTask task);

  List<GenerationTask> listByProjectId(String projectId);
}
