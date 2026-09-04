package com.ishome.project.testsupport;

import com.ishome.project.domain.port.DeliverablesPresentation;
import com.ishome.project.domain.port.DeliverablesPresenter;
import java.util.ArrayList;
import java.util.List;

/** 记录呈现请求的假会话侧；可设为"这一次没送到"。 */
public class RecordingDeliverablesPresenter implements DeliverablesPresenter {
  public final List<DeliverablesPresentation> presented = new ArrayList<>();
  private boolean delivering = true;

  public RecordingDeliverablesPresenter delivering(boolean value) {
    this.delivering = value;
    return this;
  }

  @Override
  public boolean present(DeliverablesPresentation presentation) {
    if (!delivering) {
      return false;
    }
    presented.add(presentation);
    return true;
  }
}
