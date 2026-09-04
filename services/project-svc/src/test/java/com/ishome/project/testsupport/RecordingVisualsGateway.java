package com.ishome.project.testsupport;

import com.ishome.project.domain.port.FloorplanVisualsDispatch;
import com.ishome.project.domain.port.FloorplanVisualsGateway;
import com.ishome.project.domain.port.VisualsDispatchException;
import com.ishome.project.domain.port.VisualsDispatchReceipt;
import java.util.ArrayList;
import java.util.List;

/** 记录派发报文的假网关；可设为当场失败（模拟编排侧连不上）。 */
public class RecordingVisualsGateway implements FloorplanVisualsGateway {
  public final List<FloorplanVisualsDispatch> dispatched = new ArrayList<>();
  private boolean failing;

  public RecordingVisualsGateway failing(boolean value) {
    this.failing = value;
    return this;
  }

  @Override
  public VisualsDispatchReceipt dispatch(FloorplanVisualsDispatch dispatch) {
    if (failing) {
      throw new VisualsDispatchException(dispatch.taskId(), "编排侧连不上（测试）");
    }
    dispatched.add(dispatch);
    return new VisualsDispatchReceipt(
        dispatch.taskId(), "floorplan-visuals-" + dispatch.taskId(), "run-1");
  }
}
