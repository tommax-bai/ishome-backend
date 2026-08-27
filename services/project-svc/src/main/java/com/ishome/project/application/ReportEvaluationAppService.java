package com.ishome.project.application;

import com.ishome.project.domain.port.ReleaseRepository;
import com.ishome.project.domain.rulebook.EvaluationInput;
import com.ishome.project.domain.rulebook.ReleaseNotFoundException;
import com.ishome.project.domain.rulebook.ReleaseSnapshot;
import com.ishome.project.domain.rulebook.ReportDataPackage;
import com.ishome.project.domain.rulebook.RulebookEvaluator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 求值线入口（图 v0.2 §2）：按域取最新 release 快照 → lkp- 确定性求值 → 报告数据包。 只读、无事务；调用时机 = 里程碑 on_enter 或接口请求内同步求值（规则
 * 8.1：gen-evaluated 不进异步队列）。
 */
@Service
public class ReportEvaluationAppService {

  private final ReleaseRepository releaseRepository;
  private final RulebookEvaluator evaluator = new RulebookEvaluator();

  public ReportEvaluationAppService(ReleaseRepository releaseRepository) {
    this.releaseRepository = releaseRepository;
  }

  public ReportDataPackage evaluate(List<String> domains, EvaluationInput input) {
    List<ReleaseSnapshot> snapshots =
        domains.stream()
            .map(
                domain ->
                    releaseRepository
                        .findLatest(domain)
                        .orElseThrow(() -> new ReleaseNotFoundException(domain)))
            .toList();
    return evaluator.evaluate(snapshots, input);
  }
}
