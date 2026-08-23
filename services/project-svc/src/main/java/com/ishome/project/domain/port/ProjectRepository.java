package com.ishome.project.domain.port;

import com.ishome.project.domain.Project;
import com.ishome.project.domain.ProjectNotFoundException;
import java.util.Optional;

/** 项目仓储 port（实现在 infrastructure；当前为内存占位，Flyway 数据层为路线图下一步）。 */
public interface ProjectRepository {

  void save(Project project);

  Optional<Project> findById(String projectId);

  default Project getById(String projectId) {
    return findById(projectId).orElseThrow(() -> new ProjectNotFoundException(projectId));
  }
}
