package com.ishome.project.application;

import com.ishome.project.domain.port.ReleaseRepository;
import com.ishome.project.domain.rulebook.ArtifactEntitlement;
import com.ishome.project.domain.rulebook.EvaluationInput;
import com.ishome.project.domain.rulebook.ReleaseNotFoundException;
import com.ishome.project.domain.rulebook.ReleaseSnapshot;
import com.ishome.project.domain.rulebook.ReportDataPackage;
import com.ishome.project.domain.rulebook.RulebookEvaluator;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 求值线入口（图 v0.2 §2）：按域取最新 release 快照 → lkp- 确定性求值 → 报告数据包。 只读、无事务；调用时机 = 里程碑 on_enter 或接口请求内同步求值（规则
 * 8.1：gen-evaluated 不进异步队列）。
 *
 * <p>{@code entitlement} 由调用方给：一次求值服务于哪个产物、那个产物是 FREE 还是 PAID，**只有调用方知道** （规范 §3.1 产物登记表的权益列在
 * contracts，规则 4.12"contracts 只进契约不进内容"——本服务不持有产物清单）。 语域纪律由 {@code AnchorPresentationPolicy}、标注纪律由
 * {@code AnchorProvenancePolicy} 强制执行（规则 4.10a/4.10c）。
 *
 * <p>{@code evaluatedOn}（求值基准日）同样是**入参**：时效越界的判定不读运行时时钟，否则同一份 release 同一份输入 在两天里会算出两个包，规则 8.2
 * 的字节级可重放当场失效。三参数重载取当天（UTC）——线上调用与重放各走各的口子， 重放必须显式传回原包的 {@code evaluatedOn}。
 */
@Service
public class ReportEvaluationAppService {

  private final ReleaseRepository releaseRepository;
  private final RulebookEvaluator evaluator = new RulebookEvaluator();

  public ReportEvaluationAppService(ReleaseRepository releaseRepository) {
    this.releaseRepository = releaseRepository;
  }

  /** 线上调用口：基准日取当天（UTC，同本仓时间纪律）。重放走四参数重载，传回原包的 evaluatedOn。 */
  public ReportDataPackage evaluate(
      List<String> domains, EvaluationInput input, ArtifactEntitlement entitlement) {
    return evaluate(domains, input, entitlement, LocalDate.now(ZoneOffset.UTC));
  }

  public ReportDataPackage evaluate(
      List<String> domains,
      EvaluationInput input,
      ArtifactEntitlement entitlement,
      LocalDate evaluatedOn) {
    return evaluate(domains, input, entitlement, evaluatedOn, Map.of());
  }

  /** 线上调用口 · 带必挂集：基准日同样取当天，"今天是哪天"只在本类里说一次。 */
  public ReportDataPackage evaluate(
      List<String> domains,
      EvaluationInput input,
      ArtifactEntitlement entitlement,
      Map<String, List<String>> lockedTextsByArtifact) {
    return evaluate(
        domains, input, entitlement, LocalDate.now(ZoneOffset.UTC), lockedTextsByArtifact);
  }

  /**
   * 带必挂锁定文案的求值口：{@code lockedTextsByArtifact} 是调用方按 art- 传入的那半必挂集， 与求值线派生的那半在 {@code
   * RulebookEvaluator} 内求并集（裁决⑯：必挂集以数据包清单为唯一口径）。
   */
  public ReportDataPackage evaluate(
      List<String> domains,
      EvaluationInput input,
      ArtifactEntitlement entitlement,
      LocalDate evaluatedOn,
      Map<String, List<String>> lockedTextsByArtifact) {
    List<ReleaseSnapshot> snapshots =
        domains.stream()
            .map(
                domain ->
                    releaseRepository
                        .findLatest(domain)
                        .orElseThrow(() -> new ReleaseNotFoundException(domain)))
            .toList();
    return evaluator.evaluate(snapshots, input, entitlement, evaluatedOn, lockedTextsByArtifact);
  }
}
