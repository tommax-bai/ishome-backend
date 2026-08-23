package com.ishome.shared.kernel.archrules;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.Architectures;

/**
 * Java 业务服务 DDD-lite 四层架构规则集，逐条对应《开发规范与代码分层》§1.1 的规则表。
 *
 * <p>每个业务服务放一个 ArchitectureTest 调用 {@link #check(String)}——规范不进 CI 等于不存在。
 */
public final class BusinessServiceArchRules {

  private static final String CHANNEL_BASE_PACKAGE = "com.ishome.channel";
  private static final String CHANNEL_ADAPTER_PACKAGE =
      "com.ishome.channel.infrastructure.adapter..";
  private static final String TRANSACTIONAL_SPRING =
      "org.springframework.transaction.annotation.Transactional";
  private static final String TRANSACTIONAL_JAKARTA = "jakarta.transaction.Transactional";

  private BusinessServiceArchRules() {}

  /** 对 basePackage（如 com.ishome.identity）执行全部业务服务分层规则。 */
  public static void check(String basePackage) {
    JavaClasses classes = new ClassFileImporter().importPackages(basePackage);

    // 规则①：interfaces → application → domain，infrastructure → domain，禁止反向与绕层
    // （领域规则不感知技术细节；换存储/换框架不动 domain）
    Architectures.layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .withOptionalLayers(true)
        .layer("interfaces")
        .definedBy(basePackage + ".interfaces..")
        .layer("application")
        .definedBy(basePackage + ".application..")
        .layer("domain")
        .definedBy(basePackage + ".domain..")
        .layer("infrastructure")
        .definedBy(basePackage + ".infrastructure..")
        .whereLayer("interfaces")
        .mayNotBeAccessedByAnyLayer()
        .whereLayer("application")
        .mayOnlyBeAccessedByLayers("interfaces")
        .whereLayer("infrastructure")
        .mayNotBeAccessedByAnyLayer()
        .allowEmptyShould(true)
        .check(classes);

    // 规则②：domain 层禁止 import spring-web / mybatis / servlet 任何类
    ArchRuleDefinition.noClasses()
        .that()
        .resideInAPackage(basePackage + ".domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework.web..",
            "org.apache.ibatis..",
            "com.baomidou..",
            "jakarta.servlet..",
            "javax.servlet..")
        .allowEmptyShould(true)
        .check(classes);

    // 规则③：controller 禁止直接调 repository / Mapper（事务边界必须在 application，
    // 绕层 = 事务和权限校验被绕过）
    ArchRuleDefinition.noClasses()
        .that()
        .resideInAPackage(basePackage + ".interfaces..")
        .should()
        .dependOnClassesThat(
            JavaClass.Predicates.simpleNameEndingWith("Repository")
                .or(JavaClass.Predicates.simpleNameEndingWith("RepositoryImpl"))
                .or(JavaClass.Predicates.simpleNameEndingWith("Mapper")))
        .allowEmptyShould(true)
        .check(classes);

    // 规则④：@Transactional 只允许出现在 application 层（事务边界唯一化）
    ArchRuleDefinition.noClasses()
        .that()
        .resideOutsideOfPackage(basePackage + ".application..")
        .should()
        .beAnnotatedWith(TRANSACTIONAL_SPRING)
        .orShould()
        .beAnnotatedWith(TRANSACTIONAL_JAKARTA)
        .allowEmptyShould(true)
        .check(classes);
    ArchRuleDefinition.noMethods()
        .that()
        .areDeclaredInClassesThat()
        .resideOutsideOfPackage(basePackage + ".application..")
        .should()
        .beAnnotatedWith(TRANSACTIONAL_SPRING)
        .orShould()
        .beAnnotatedWith(TRANSACTIONAL_JAKARTA)
        .allowEmptyShould(true)
        .check(classes);

    // 规则⑥（规范 §6.2 白名单①）：渠道 adapter 包只许 channel-svc 内部使用——
    // 包即可插拔单元，其他服务 import adapter 即渠道方言泄漏
    if (!basePackage.equals(CHANNEL_BASE_PACKAGE)) {
      ArchRuleDefinition.noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAPackage(CHANNEL_ADAPTER_PACKAGE)
          .allowEmptyShould(true)
          .check(classes);
    }
  }
}
