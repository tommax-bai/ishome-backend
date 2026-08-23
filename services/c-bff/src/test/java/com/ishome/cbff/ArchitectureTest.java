package com.ishome.cbff;

import com.ishome.shared.kernel.archrules.BffArchRules;
import org.junit.jupiter.api.Test;

/** BFF 规则由 shared/kernel 统一提供（开发规范 §1.2）：无 domain、无库、无事务。 */
class ArchitectureTest {

  @Test
  void enforcesBffRules() {
    BffArchRules.check("com.ishome.cbff");
  }
}
