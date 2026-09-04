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

  @Select(
      "SELECT * FROM projects WHERE owner_channel_type = #{channelType} AND owner_channel_instance ="
          + " #{channelInstance} AND owner_external_user_id = #{externalUserId} AND status = 'ACTIVE'"
          + " AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 1")
  ProjectPO findActiveByOwner(
      @Param("channelType") String channelType,
      @Param("channelInstance") String channelInstance,
      @Param("externalUserId") String externalUserId);
}
