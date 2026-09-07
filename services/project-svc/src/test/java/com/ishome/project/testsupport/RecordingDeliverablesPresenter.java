package com.ishome.project.testsupport;

import com.ishome.project.domain.port.DeliverablesPresentation;
import com.ishome.project.domain.port.DeliverablesPresenter;
import java.util.ArrayList;
import java.util.List;

/** 记录呈现请求的假会话侧；可设为"这一次按幂等跳过"（delivering=false）或"连不上"（throwing=true）。 */
public class RecordingDeliverablesPresenter implements DeliverablesPresenter {
  public final List<DeliverablesPresentation> presented = new ArrayList<>();
  private boolean delivering = true;
  private boolean throwing;

  public RecordingDeliverablesPresenter delivering(boolean value) {
    this.delivering = value;
    return this;
  }

  /** 会话侧连不上：rpc 抛异常——真送不到的唯一形态。 */
  public RecordingDeliverablesPresenter throwing(boolean value) {
    this.throwing = value;
    return this;
  }

  @Override
  public boolean present(DeliverablesPresentation presentation) {
    if (throwing) {
      throw new IllegalStateException("会话侧连不上（测试）");
    }
    if (!delivering) {
      return false;
    }
    presented.add(presentation);
    return true;
  }
}
