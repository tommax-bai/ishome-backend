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
// contracts 生成代码纳入（源目录来自 ishome-contracts 检出，见该模块 build 文件；禁手写客户端）
include(":shared:contracts")

// 业务服务（DDD-lite 四层，开发规范 §1.1）；新增服务用 scripts/new-service.sh 生成，不手搭
include(":services:identity-svc")
include(":services:estate-svc")
include(":services:content-svc")
include(":services:trade-svc")
include(":services:shelf-svc")
include(":services:channel-svc")
// V1.5 裁决：design-svc 一拆为二——chat-svc（Python，aipipe 仓）+ project-svc（本仓，项目唯一真相）
include(":services:project-svc")

// BFF（三层，无 domain，开发规范 §1.2）
include(":services:c-bff")
include(":services:admin-bff")
