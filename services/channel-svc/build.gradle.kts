plugins {
    id("ishome.service-conventions")
}

description = "channel-svc — IM 渠道网关（可插拔 adapter，首发飞书） + 触达策略与频控"

dependencies {
    implementation(project(":shared:contracts"))
}
