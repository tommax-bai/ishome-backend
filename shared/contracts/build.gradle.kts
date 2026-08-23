// contracts 生成代码纳入模块：唯一真源在 ishome-contracts 仓（buf generate 产物），本模块零手写代码。
// 源目录经 -PcontractsRepoPath 覆盖（默认同级检出 ../ishome-contracts）；CI 检出 v0.1.1 tag 后传入。
// 不套 ishome.java-conventions —— Checkstyle/PMD/Spotless 约束手写代码，不约束 buf 产物。
plugins {
    `java-library`
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

val contractsRepoPath = providers.gradleProperty("contractsRepoPath").getOrElse("../ishome-contracts")
val contractsDir = File(contractsRepoPath).let { if (it.isAbsolute) it else rootDir.resolve(it) }
val contractsJavaSrc = contractsDir.resolve("gen/java/src/main/java")

require(contractsJavaSrc.isDirectory) {
    "contracts 检出不存在：$contractsDir —— clone ishome-contracts 到 backend 同级目录，" +
        "或传 -PcontractsRepoPath=<检出路径>（CI 见 .github/workflows/ci.yml）"
}

sourceSets {
    main {
        java.srcDir(contractsJavaSrc)
    }
}

dependencies {
    api(libs.protobuf.java)
    api(libs.grpc.stub)
    api(libs.grpc.protobuf)
    // grpc 生成代码的 @javax.annotation.Generated（仅编译期）
    compileOnly(libs.annotations.api)
}
