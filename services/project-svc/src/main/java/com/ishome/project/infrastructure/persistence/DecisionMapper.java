package com.ishome.project.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** svc_project.decisions Mapper。 */
@Mapper
public interface DecisionMapper extends BaseMapper<DecisionPO> {

  @Select("SELECT * FROM decisions WHERE project_id = #{projectId} AND deleted_at IS NULL")
  List<DecisionPO> listActiveByProjectId(@Param("projectId") String projectId);
}
