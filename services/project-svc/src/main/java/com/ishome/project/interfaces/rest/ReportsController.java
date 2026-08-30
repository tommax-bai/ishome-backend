package com.ishome.project.interfaces.rest;

import com.ishome.project.application.ReportBookLinkAppService;
import com.ishome.project.application.ReportDispatchAppService;
import com.ishome.project.domain.rulebook.ReleaseNotFoundException;
import com.ishome.project.domain.rulebook.ReportDispatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
  private final ReportBookLinkAppService reportBookLinkAppService;

  public ReportsController(
      ReportDispatchAppService reportDispatchAppService,
      ReportBookLinkAppService reportBookLinkAppService) {
    this.reportDispatchAppService = reportDispatchAppService;
    this.reportBookLinkAppService = reportBookLinkAppService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ReportDispatchResponse dispatch(@RequestBody ReportDispatchRequest request) {
    return ReportDispatchResponse.from(reportDispatchAppService.dispatch(request.toCommand()));
  }

  /**
   * 取这份报告册的可打开链接。
   *
   * <p>**404 = 还没出册**，不是"没这份报告"：报告是异步生成的，问早了本来就该是"还没有"。 出册与否直接问存储——键由 report_id 确定性推得，不查任何台账（规则 8.1
   * 禁第二台状态机）。
   *
   * <p>链接自带有效期；业主拿到的是"这一份、这段时间内"的通行证。当前**没有认人这一步** （触发条件写死＝开始接外部真实用户时），所以口径如实是"拿到链接的人就能打开，且会过期"。
   */
  @GetMapping("/{reportId}/link")
  public ResponseEntity<ReportBookLinkResponse> link(@PathVariable String reportId) {
    return reportBookLinkAppService
        .issueLink(reportId)
        .map(ReportBookLinkResponse::from)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
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
