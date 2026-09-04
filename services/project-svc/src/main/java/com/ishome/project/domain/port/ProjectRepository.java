package com.ishome.project.domain.port;

import com.ishome.project.domain.Project;
import com.ishome.project.domain.ProjectNotFoundException;
import com.ishome.project.domain.ProjectOwner;
import java.util.Optional;

/** 项目仓储 port（实现在 infrastructure；当前为内存占位，Flyway 数据层为路线图下一步）。 */
public interface ProjectRepository {

  void save(Project project);

  Optional<Project> findById(String projectId);

  /** 按会话属主取进行中的项目（同一属主至多一个 ACTIVE）。find = 可空。 */
  Optional<Project> findActiveByOwner(ProjectOwner owner);

  default Project getById(String projectId) {
    return findById(projectId).orElseThrow(() -> new ProjectNotFoundException(projectId));
  }
}
