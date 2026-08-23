plugins {
    id("ishome.library-conventions")
}

description = "共享领域原语 + ArchUnit 架构规则集（经 testFixtures 提供给各服务的 ArchitectureTest）"

dependencies {
    testFixturesApi(libs.archunit.junit5)
}
