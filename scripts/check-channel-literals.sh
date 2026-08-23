#!/usr/bin/env bash
# 渠道名三处白名单的 CI 执行（开发规范 §5.1 R4 / §6.2）：
# 渠道名字面量只许出现在 ①channel-svc adapter 包 ②配置键 ③数据字段值。
# 其余服务源码 grep 到渠道名即失败——"design-svc 不许有渠道分支"的代码侧兑现。
# 注册表新增渠道时，同步扩这里的 pattern（清单由 contracts 仓 ChannelType 注册表生成）。
set -euo pipefail
cd "$(dirname "$0")/.."

pattern='"(feishu|lark|wecom|wechat_oa|wechat_mini|sms|mock)"'
hits="$(grep -rnE "$pattern" services --include='*.java' 2>/dev/null | grep -v '^services/channel-svc/' | grep -v '/src/test/' || true)"

if [ -n "$hits" ]; then
  echo "渠道名字面量出现在白名单之外（开发规范 §6.2，R4）：" >&2
  echo "$hits" >&2
  exit 1
fi
echo "channel literal check OK"
