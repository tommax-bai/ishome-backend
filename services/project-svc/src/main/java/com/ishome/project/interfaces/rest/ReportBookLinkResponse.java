package com.ishome.project.interfaces.rest;

import com.ishome.project.domain.rulebook.ReportBookLink;
import java.time.Instant;

/** 报告册链接的响应体：一条能打开的地址 + 它什么时候失效（失效时间必须给，业主才知道要不要现在看）。 */
public record ReportBookLinkResponse(String url, Instant expiresAt) {

  static ReportBookLinkResponse from(ReportBookLink link) {
    return new ReportBookLinkResponse(link.url().toString(), link.expiresAt());
  }
}
