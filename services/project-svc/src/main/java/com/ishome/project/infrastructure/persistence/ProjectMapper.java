package com.ishome.project.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** svc_project.projects Mapper。 */
@Mapper
public interface ProjectMapper extends BaseMapper<ProjectPO> {

  @Select("SELECT * FROM projects WHERE id = #{projectId} AND deleted_at IS NULL")
  ProjectPO findActiveById(@Param("projectId") String projectId);
}
