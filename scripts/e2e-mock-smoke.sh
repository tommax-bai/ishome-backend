#!/usr/bin/env bash
# 第一条纵切的跨语言 E2E 冒烟：mock 渠道注入一条用户消息 → channel-svc → design-svc 回话 → mock 出站捕获。
# 前置：../ishome-aipipe 已 uv sync；本机 9101 被占用，design-svc 用 19101（联调约定）。
# 用法：scripts/e2e-mock-smoke.sh
set -euo pipefail
cd "$(dirname "$0")/.."

# 本机 JDK：gradlew 启动需要 JVM 17+，而本机 shell 配置把 JAVA_HOME 全局指向 1.8——
# 因此只要 brew 的 JDK 21 存在就强制使用（ISHOME_JAVA_HOME 可覆盖）。
if [ -n "${ISHOME_JAVA_HOME:-}" ]; then
  export JAVA_HOME="$ISHOME_JAVA_HOME"
elif [ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi

DESIGN_PORT="${DESIGN_PORT:-19101}"
CHANNEL_HTTP="http://localhost:8102"
LOG_DIR="$(mktemp -d /tmp/ishome-e2e.XXXXXX)"
DESIGN_LOG="$LOG_DIR/design-svc.log"
CHANNEL_LOG="$LOG_DIR/channel-svc.log"
EXPECT_PREFIX='[design-svc] 收到你的消息：'

cleanup() {
  echo "== cleanup（日志留存 ${LOG_DIR}）"
  [ -n "${CHANNEL_PID:-}" ] && { pkill -P "$CHANNEL_PID" 2>/dev/null || true; kill "$CHANNEL_PID" 2>/dev/null || true; }
  [ -n "${DESIGN_PID:-}" ] && { pkill -P "$DESIGN_PID" 2>/dev/null || true; kill "$DESIGN_PID" 2>/dev/null || true; }
  # bootRun/uv 的孙进程兜底
  pkill -f "com.ishome.channel.ChannelApplication" 2>/dev/null || true
  pkill -f "design-grpc" 2>/dev/null || true
}
trap cleanup EXIT

wait_for() { # wait_for <描述> <超时秒> <命令...>
  local desc="$1" timeout_seconds="$2"; shift 2
  for _ in $(seq 1 "$timeout_seconds"); do
    if "$@" >/dev/null 2>&1; then echo "== $desc 就绪"; return 0; fi
    sleep 1
  done
  echo "!! $desc 超时（${timeout_seconds}s）" >&2
  tail -30 "$DESIGN_LOG" "$CHANNEL_LOG" 2>/dev/null >&2 || true
  return 1
}

echo "== 启动 design-svc（gRPC :${DESIGN_PORT}）"
(cd ../ishome-aipipe && DESIGN_GRPC_PORT="$DESIGN_PORT" uv run design-grpc) >"$DESIGN_LOG" 2>&1 &
DESIGN_PID=$!
wait_for "design-svc" 60 grep -q "design-svc gRPC listening" "$DESIGN_LOG"

echo "== 启动 channel-svc（local profile，HTTP :8102 / gRPC :9102 → design :${DESIGN_PORT}）"
./gradlew :services:channel-svc:bootRun \
  --args="--spring.profiles.active=local --ishome.channel.design-target=localhost:$DESIGN_PORT" \
  >"$CHANNEL_LOG" 2>&1 &
CHANNEL_PID=$!
wait_for "channel-svc" 120 curl -sf "$CHANNEL_HTTP/mock/channels/outbound"

echo "== 注入入站消息"
curl -sf -X POST "$CHANNEL_HTTP/mock/channels/inbound" \
  -H 'Content-Type: application/json' \
  -d '{"user_id":"u-e2e","text":"你好，我想设计我的家"}'
echo

echo "== 等待 design-svc 回话进入 mock 出站捕获"
for _ in $(seq 1 60); do
  outbound="$(curl -sf "$CHANNEL_HTTP/mock/channels/outbound" || echo '')"
  if [[ "$outbound" == *"$EXPECT_PREFIX"* ]]; then
    echo "== E2E PASS：收到回话"
    echo "$outbound"
    exit 0
  fi
  sleep 1
done

echo "!! E2E FAIL：60s 内未捕获到 '$EXPECT_PREFIX'" >&2
echo "-- 最近出站捕获：$(curl -s "$CHANNEL_HTTP/mock/channels/outbound" || true)" >&2
tail -40 "$DESIGN_LOG" "$CHANNEL_LOG" >&2 || true
exit 1
