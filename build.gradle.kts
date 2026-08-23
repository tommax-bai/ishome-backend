// 根项目无代码；公共构建逻辑全部在 buildSrc 约定插件（规范即工具链）
tasks.wrapper {
    distributionType = Wrapper.DistributionType.BIN
}
