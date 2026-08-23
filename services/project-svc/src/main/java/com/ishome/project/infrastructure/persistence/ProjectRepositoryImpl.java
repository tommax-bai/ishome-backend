package com.ishome.project.infrastructure.persistence;

import com.ishome.project.domain.Project;
import com.ishome.project.domain.ProjectStatus;
import com.ishome.project.domain.port.ProjectRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** svc_project.projects PG 实现：save 为按 id 的 insert-or-update。 */
@Repository
public class ProjectRepositoryImpl implements ProjectRepository {

  private final ProjectMapper projectMapper;

  public ProjectRepositoryImpl(ProjectMapper projectMapper) {
    this.projectMapper = projectMapper;
  }

  @Override
  public void save(Project project) {
    ProjectPO po = toPo(project);
    if (projectMapper.selectById(project.id()) == null) {
      projectMapper.insert(po);
    } else {
      po.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
      projectMapper.updateById(po);
    }
  }

  @Override
  public Optional<Project> findById(String projectId) {
    return Optional.ofNullable(projectMapper.findActiveById(projectId)).map(this::toDomain);
  }

  private ProjectPO toPo(Project project) {
    ProjectPO po = new ProjectPO();
    po.setId(project.id());
    po.setUserId(project.userId());
    po.setFloorplanRef(project.floorplanRef());
    po.setProcessVersion(project.processVersion());
    po.setCurrentMilestone(project.currentMilestone());
    po.setStatus(project.status().name());
    return po;
  }

  private Project toDomain(ProjectPO po) {
    return new Project(
        po.getId(),
        po.getUserId(),
        po.getFloorplanRef(),
        po.getProcessVersion(),
        po.getCurrentMilestone(),
        ProjectStatus.valueOf(po.getStatus()));
  }
}
