package com.ishome.shared.kernel.archrules;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.Architectures;

/**
 * BFF 架构规则集（《开发规范与代码分层》§1.2）：interfaces → aggregation → clients 三层， 无 domain、无库、无事务——BFF
 * 里出现业务规则就是分层泄漏，规则下沉到域服务。
 */
public final class BffArchRules {

  private static final String TRANSACTIONAL_SPRING =
      "org.springframework.transaction.annotation.Transactional";
  private static final String TRANSACTIONAL_JAKARTA = "jakarta.transaction.Transactional";

  private BffArchRules() {}

  /** 对 basePackage（如 com.ishome.cbff）执行全部 BFF 规则。 */
  public static void check(String basePackage) {
    JavaClasses classes = new ClassFileImporter().importPackages(basePackage);

    // BFF 没有 domain 层
    for (JavaClass clazz : classes) {
      if (clazz.getPackageName().startsWith(basePackage + ".domain")) {
        throw new AssertionError("BFF 不得出现 domain 层（开发规范 §1.2）：业务规则下沉到域服务，违规类 " + clazz.getName());
      }
    }

    // interfaces → aggregation → clients 单向；controller 不越过 aggregation 直连 client
    Architectures.layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .withOptionalLayers(true)
        .layer("interfaces")
        .definedBy(basePackage + ".interfaces..")
        .layer("aggregation")
        .definedBy(basePackage + ".aggregation..")
        .layer("clients")
        .definedBy(basePackage + ".clients..")
        .whereLayer("interfaces")
        .mayNotBeAccessedByAnyLayer()
        .whereLayer("aggregation")
        .mayOnlyBeAccessedByLayers("interfaces")
        .whereLayer("clients")
        .mayOnlyBeAccessedByLayers("aggregation")
        .allowEmptyShould(true)
        .check(classes);

    // BFF 无库：禁止依赖 mybatis / flyway（出现即报警——BFF 无库是结构性事实）
    ArchRuleDefinition.noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.apache.ibatis..", "com.baomidou..", "org.flywaydb..")
        .allowEmptyShould(true)
        .check(classes);

    // BFF 无事务：禁止 @Transactional
    ArchRuleDefinition.noClasses()
        .should()
        .beAnnotatedWith(TRANSACTIONAL_SPRING)
        .orShould()
        .beAnnotatedWith(TRANSACTIONAL_JAKARTA)
        .allowEmptyShould(true)
        .check(classes);
    ArchRuleDefinition.noMethods()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage(basePackage + "..")
        .should()
        .beAnnotatedWith(TRANSACTIONAL_SPRING)
        .orShould()
        .beAnnotatedWith(TRANSACTIONAL_JAKARTA)
        .allowEmptyShould(true)
        .check(classes);
  }
}
