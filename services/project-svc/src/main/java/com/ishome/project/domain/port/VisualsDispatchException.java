package com.ishome.project.domain.port;

/**
 * 三张图派发失败：任务没能交到编排侧手上（连不上、超时、非 2xx、回执解析不了）。
 *
 * <p>响亮失败：任务已落表为 FAILED、业主会被告知"没做出来"，而不是挂在 PENDING 上永远等一个不会来的回流。
 */
public class VisualsDispatchException extends RuntimeException {
  private final String taskId;

  public VisualsDispatchException(String taskId, String message, Throwable cause) {
    super(message, cause);
    this.taskId = taskId;
  }

  public VisualsDispatchException(String taskId, String message) {
    this(taskId, message, null);
  }

  public String taskId() {
    return taskId;
  }
}
