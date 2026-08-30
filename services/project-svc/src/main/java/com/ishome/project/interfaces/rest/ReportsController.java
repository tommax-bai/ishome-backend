package com.ishome.project.interfaces.rest;

import com.ishome.project.application.ReportDispatchAppService;
import com.ishome.project.domain.rulebook.ReleaseNotFoundException;
import com.ishome.project.domain.rulebook.ReportDispatchException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报告生成触发口：求值线与成文线的一键接通（图 v0.2 §2）。
 *
 * <p>**202 不是"报告好了"**，是"这份包已经交到成文线手上了"。成文是一次短 run，结论经回流写回里程碑 （规则 8.1），不在本请求内等待——想知道报告成没成，问里程碑，不问这里。
 *
 * <p>调用时机由里程碑引擎决定（checkCompletion 判定应出产物集之后）。**本接口不判里程碑、不建任务**—— 它只是把"现在给我出这份报告"这句话变成一次派发。
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportsController {

  private final ReportDispatchAppService reportDispatchAppService;

  public ReportsController(ReportDispatchAppService reportDispatchAppService) {
    this.reportDispatchAppService = reportDispatchAppService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ReportDispatchResponse dispatch(@RequestBody ReportDispatchRequest request) {
    return ReportDispatchResponse.from(reportDispatchAppService.dispatch(request.toCommand()));
  }

  @ExceptionHandler(ReleaseNotFoundException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  public void onReleaseNotFound() {
    // 422，无响应体：域没有 release 快照＝这个域还没发布过，改数据不改请求
  }

  @ExceptionHandler(ReportDispatchException.class)
  @ResponseStatus(HttpStatus.BAD_GATEWAY)
  public void onDispatchFailed() {
    // 502，无响应体：包是好的，是下游没接住——责任在编排侧那一跳，不要让调用方以为自己传错了
  }
}
