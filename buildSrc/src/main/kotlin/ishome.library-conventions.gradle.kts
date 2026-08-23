// 共享库模块（shared/kernel、shared/starter）：纯 library，不打 Boot 镜像
plugins {
    id("ishome.java-conventions")
    `java-library`
    `java-test-fixtures`
}
