package com.ishome.shared.kernel.archrules;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** 规则集自检：合规样例通过、违规样例被拦——保证规则真的在执行，而不是恒真空转。 */
class BusinessServiceArchRulesTest {

  private static final String SAMPLES = "com.ishome.shared.kernel.archrules.samples";

  @Test
  void acceptsConformingLayering() {
    assertDoesNotThrow(() -> BusinessServiceArchRules.check(SAMPLES + ".good"));
  }

  @Test
  void rejectsControllerDependingOnInfrastructure() {
    assertThrows(AssertionError.class, () -> BusinessServiceArchRules.check(SAMPLES + ".bad"));
  }
}
