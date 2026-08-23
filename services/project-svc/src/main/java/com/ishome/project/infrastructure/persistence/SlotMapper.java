package com.ishome.project.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** svc_project.slots Mapper：(project_id, slot_key) 幂等 upsert——同一槽位后写覆盖前写。 */
@Mapper
public interface SlotMapper {

  @Insert(
      "INSERT INTO slots (id, project_id, slot_key, value, status, source_event_id, confidence,"
          + " stage) VALUES (#{id}, #{projectId}, #{slotKey}, #{value}, #{status},"
          + " #{sourceEventId}, #{confidence}, #{stage}) ON CONFLICT (project_id, slot_key) DO"
          + " UPDATE SET value = EXCLUDED.value, status = EXCLUDED.status, source_event_id ="
          + " EXCLUDED.source_event_id, confidence = EXCLUDED.confidence, stage = EXCLUDED.stage,"
          + " updated_at = now()")
  void upsert(SlotPO slot);

  @Select("SELECT * FROM slots WHERE project_id = #{projectId} AND deleted_at IS NULL")
  List<SlotPO> listActiveByProjectId(@Param("projectId") String projectId);
}
