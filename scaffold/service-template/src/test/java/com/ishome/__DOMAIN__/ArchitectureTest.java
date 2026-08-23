package com.ishome.__DOMAIN__;

import com.ishome.shared.kernel.archrules.BusinessServiceArchRules;
import org.junit.jupiter.api.Test;

/** 分层规则由 shared/kernel 统一提供（开发规范 §1.1）——规范不进 CI 等于不存在。 */
class ArchitectureTest {

  @Test
  void enforcesLayeredArchitecture() {
    BusinessServiceArchRules.check("com.ishome.__DOMAIN__");
  }
}
