import org.gradle.api.artifacts.VersionCatalogsExtension

// 全模块公共约定：Java 21 工具链 + Checkstyle/PMD/Spotless + JUnit5 + ArchUnit
// 规范即工具链（技术架构总纲第 3 条）：写在文档不进流水线的规范视为不存在
plugins {
    java
    checkstyle
    pmd
    id("com.diffplug.spotless")
}

group = "com.ishome"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    "testImplementation"(platform(libs.findLibrary("junit-bom").get()))
    "testImplementation"("org.junit.jupiter:junit-jupiter")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    "testImplementation"(libs.findLibrary("archunit-junit5").get())
}

tasks.withType<Test> {
    useJUnitPlatform()
}

checkstyle {
    toolVersion = libs.findVersion("checkstyle").get().requiredVersion
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    // error 级违规（禁用后缀、裸 DTO）挂 CI；warning 级（量纲后缀 best-effort）只提示
    maxErrors = 0
}

pmd {
    toolVersion = libs.findVersion("pmd").get().requiredVersion
    ruleSetFiles = rootProject.files("config/pmd/ruleset.xml")
    ruleSets = emptyList()
    isConsoleOutput = true
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat(libs.findVersion("googleJavaFormat").get().requiredVersion)
    }
}
