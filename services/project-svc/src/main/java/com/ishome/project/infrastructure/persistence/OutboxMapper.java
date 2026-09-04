package com.ishome.project.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** svc_project.outbox Mapper：取未投递（按发生时间）、标已投递。 */
@Mapper
public interface OutboxMapper extends BaseMapper<OutboxPO> {
  @Select(
      "SELECT * FROM outbox WHERE published_at IS NULL AND deleted_at IS NULL ORDER BY occurred_at"
          + " LIMIT #{limit}")
  List<OutboxPO> listUnpublished(@Param("limit") int limit);

  @Update("UPDATE outbox SET published_at = now(), updated_at = now() WHERE id = #{eventId}")
  int markPublished(@Param("eventId") String eventId);
}
