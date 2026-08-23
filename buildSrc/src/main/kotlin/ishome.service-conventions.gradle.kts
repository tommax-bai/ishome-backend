import org.gradle.api.artifacts.VersionCatalogsExtension
import org.springframework.boot.gradle.plugin.SpringBootPlugin

// Java 业务服务约定（identity/estate/content/trade/shelf/channel）：
// Spring Boot 3 + MyBatis-Plus + Flyway + Postgres（待拍板②默认 PG）
// 分层规则由 shared/kernel ArchUnit 规则集在各服务 ArchitectureTest 强制
plugins {
    id("ishome.java-conventions")
    id("org.springframework.boot")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    "implementation"(platform(SpringBootPlugin.BOM_COORDINATES))
    "implementation"("org.springframework.boot:spring-boot-starter-web")
    "implementation"("org.springframework.boot:spring-boot-starter-validation")
    "implementation"(libs.findLibrary("mybatis-plus-starter").get())
    "implementation"(libs.findLibrary("flyway-core").get())
    "runtimeOnly"(libs.findLibrary("flyway-postgresql").get())
    "runtimeOnly"(libs.findLibrary("postgresql").get())
    "implementation"(libs.findLibrary("ulid-creator").get())
    "implementation"(project(":shared:kernel"))
    "implementation"(project(":shared:starter"))

    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    "testImplementation"("org.testcontainers:junit-jupiter")
    "testImplementation"(testFixtures(project(":shared:kernel")))
}
