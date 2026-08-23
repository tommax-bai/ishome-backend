package com.ishome.project.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** svc_project.generation_tasks Mapper。 */
@Mapper
public interface GenerationTaskMapper extends BaseMapper<GenerationTaskPO> {

  @Select("SELECT * FROM generation_tasks WHERE project_id = #{projectId} AND deleted_at IS NULL")
  List<GenerationTaskPO> listActiveByProjectId(@Param("projectId") String projectId);
}
