package com.ishome.project.domain.port;

import com.ishome.project.domain.Slot;
import java.util.List;

/** 槽位真相仓储 port：按 (projectId, slotKey) 幂等 upsert——同一槽位后写覆盖前写。 */
public interface SlotRepository {

  void save(Slot slot);

  List<Slot> listByProjectId(String projectId);
}
