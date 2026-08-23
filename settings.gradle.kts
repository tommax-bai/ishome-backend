pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Java 21 工具链自动供给（本机无对应 JDK 时自动下载），保证任意机器可复现
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ishome-backend"

include(":shared:kernel")
include(":shared:starter")

// 业务服务（DDD-lite 四层，开发规范 §1.1）；新增服务用 scripts/new-service.sh 生成，不手搭
include(":services:identity-svc")
include(":services:estate-svc")
include(":services:content-svc")
include(":services:trade-svc")
include(":services:shelf-svc")
include(":services:channel-svc")

// BFF（三层，无 domain，开发规范 §1.2）
include(":services:c-bff")
include(":services:admin-bff")
