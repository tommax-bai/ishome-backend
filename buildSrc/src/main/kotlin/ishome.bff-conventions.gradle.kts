import org.springframework.boot.gradle.plugin.SpringBootPlugin

// BFF 约定（c-bff / admin-bff，开发规范 §1.2）：
// 无 domain 层、无数据库、无事务——BFF 出现业务规则就是分层泄漏，规则下沉域服务。
// 禁令由 shared/kernel BffArchRules 强制（无 @Transactional、无 mybatis/flyway 依赖）
plugins {
    id("ishome.java-conventions")
    id("org.springframework.boot")
}

dependencies {
    "implementation"(platform(SpringBootPlugin.BOM_COORDINATES))
    "implementation"("org.springframework.boot:spring-boot-starter-web")
    "implementation"(project(":shared:kernel"))

    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    "testImplementation"(testFixtures(project(":shared:kernel")))
}
