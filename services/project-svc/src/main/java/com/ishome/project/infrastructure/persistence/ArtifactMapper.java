package com.ishome.project.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** svc_project.artifacts Mapper。 */
@Mapper
public interface ArtifactMapper extends BaseMapper<ArtifactPO> {

  @Select("SELECT * FROM artifacts WHERE id = #{artifactId} AND deleted_at IS NULL")
  ArtifactPO findActiveById(@Param("artifactId") String artifactId);

  @Select("SELECT * FROM artifacts WHERE project_id = #{projectId} AND deleted_at IS NULL")
  List<ArtifactPO> listActiveByProjectId(@Param("projectId") String projectId);
}
