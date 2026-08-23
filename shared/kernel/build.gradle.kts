plugins {
    id("ishome.library-conventions")
}

description = "共享领域原语 + ArchUnit 架构规则集（经 testFixtures 提供给各服务的 ArchitectureTest）"

dependencies {
    testFixturesApi(libs.archunit.junit5)
    // 持久化集成测试条件（@EnabledIfLocalPostgres）：本地 PG 不可达时跳过而非红，CI 无 PG 亦绿
    testFixturesApi(platform(libs.junit.bom))
    testFixturesApi("org.junit.jupiter:junit-jupiter-api")
}
