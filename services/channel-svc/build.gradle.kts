plugins {
    id("ishome.service-conventions")
}

description = "channel-svc — IM 渠道网关（可插拔 adapter，首发飞书） + 触达策略与频控"

dependencies {
    implementation(project(":shared:contracts"))
    // gRPC 服务端/客户端运行时（ChannelService 入站 + DesignService 出站共用）
    implementation(libs.grpc.netty.shaded)
    // 飞书开放平台 SDK——仅 infrastructure.adapter.feishu 包内使用（渠道方言隔离，规范 §6.2）
    implementation(libs.oapi.sdk)
}
