package com.ishome.project.infrastructure.persistence;

import com.github.f4b6a3.ulid.UlidCreator;
import com.ishome.project.domain.CognitiveState;
import com.ishome.project.domain.Slot;
import com.ishome.project.domain.port.SlotRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** svc_project.slots PG 实现：(project_id, slot_key) 幂等 upsert，行 id 为 infrastructure 细节。 */
@Repository
public class SlotRepositoryImpl implements SlotRepository {

  private final SlotMapper slotMapper;

  public SlotRepositoryImpl(SlotMapper slotMapper) {
    this.slotMapper = slotMapper;
  }

  @Override
  public void save(Slot slot) {
    SlotPO po = new SlotPO();
    po.setId(UlidCreator.getUlid().toString());
    po.setProjectId(slot.projectId());
    po.setSlotKey(slot.slotKey());
    po.setValue(slot.value());
    po.setStatus(slot.cognitiveState().name());
    po.setSourceEventId(slot.sourceEventId());
    po.setConfidence(slot.confidence());
    po.setStage(slot.stage());
    slotMapper.upsert(po);
  }

  @Override
  public List<Slot> listByProjectId(String projectId) {
    return slotMapper.listActiveByProjectId(projectId).stream().map(this::toDomain).toList();
  }

  private Slot toDomain(SlotPO po) {
    return new Slot(
        po.getProjectId(),
        po.getSlotKey(),
        po.getValue(),
        CognitiveState.valueOf(po.getStatus()),
        po.getSourceEventId(),
        po.getConfidence(),
        po.getStage());
  }
}
