package com.ishome.project.application;

import com.ishome.project.domain.port.ReportBookStore;
import com.ishome.project.domain.rulebook.ReportBookLink;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 取一份报告册的可打开链接。
 *
 * <p>**不落库、无事务**：这一侧不存报告状态——存了就是第二台状态机（规则 8.1）。 "这份出没出册"直接问存储，键由 report_id 确定性推得（contracts {@code
 * registries/object_keys.md}）， 所以不需要任何台账就能回答；台账会与真相漂移，派生不会。
 *
 * <p>有效期是**系统策略不是每人一议**：配置项给一个值，全体一样。当前默认七天—— 自测阶段业主是在 IM 里收到一条链接，隔几天再点开是常事，而现在还没有"认人"这一步（触发条件写死＝
 * 开始接外部真实用户时），没法在他点开的那一刻现签一条新的。**认人上线后这条有效期改短、每次现签**， 那时"只有他能打开"才真正成立；在那之前如实的口径是"拿到链接的人就能打开，且会过期"。
 */
@Service
public class ReportBookLinkAppService {

  private final ReportBookStore reportBookStore;
  private final Duration validity;

  public ReportBookLinkAppService(
      ReportBookStore reportBookStore,
      @Value("${ishome.oss.book-link-validity:P7D}") Duration validity) {
    this.reportBookStore = reportBookStore;
    this.validity = validity;
  }

  public Optional<ReportBookLink> issueLink(String reportId) {
    return reportBookStore.issueLink(reportId, validity);
  }
}
