package com.ishome.project.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** svc_project.revision_log Mapper：计数即修订预算判定的已用轮数。 */
@Mapper
public interface RevisionLogMapper extends BaseMapper<RevisionLogPO> {

  @Select(
      "SELECT count(*) FROM revision_log WHERE project_id = #{projectId}"
          + " AND milestone = #{milestone} AND deleted_at IS NULL")
  int countActiveByProjectIdAndMilestone(
      @Param("projectId") String projectId, @Param("milestone") String milestone);
}
