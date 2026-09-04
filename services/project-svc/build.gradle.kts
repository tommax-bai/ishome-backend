plugins {
    id("ishome.service-conventions")
}

description =
    "project-svc — 项目唯一真相（V1.5）：里程碑引擎（事件驱动 checkCompletion）、slot/artifact/task/revision、" +
        "修订预算判定、流程定义权威分发。不理解自然语言（无任何 NLP/LLM 依赖）；支付与权益归 trade-svc"

dependencies {
    // contracts 生成代码：DesignService.PresentDeliverables 出站 stub（产物呈现那一跳，2026-09-04 接线）
    implementation(project(":shared:contracts"))
    implementation(libs.grpc.netty.shaded)
    // 私有产物签名链接：册在 OSS 私有桶里，业主靠签名链接打开（裁决 2026-08-30 晚）。
    // **只在本服务用**——签名是"给谁看、看多久"的事，属业务侧；生成侧只写不签。
    // 用官方 SDK 不自己拼签名：签错的形态是"每一份报告的链接都打不开"，
    // 而这条链路存在的全部理由就是让业主打得开。
    implementation(libs.aliyun.oss)
}
