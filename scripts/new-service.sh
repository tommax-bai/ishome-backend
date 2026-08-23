#!/usr/bin/env bash
# 用法: scripts/new-service.sh <domain>
# 例:   scripts/new-service.sh estate  →  生成 services/estate-svc（四层 + ArchitectureTest + Flyway 目录）
# 分层靠模板生成，不靠人记（开发规范 §七）。
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "用法: $0 <domain>（小写，如 estate）" >&2
  exit 1
fi

domain="$1"
if ! [[ "$domain" =~ ^[a-z][a-z0-9]*$ ]]; then
  echo "domain 必须是小写字母开头的 [a-z0-9]+：$domain" >&2
  exit 1
fi

cd "$(dirname "$0")/.."
dest="services/${domain}-svc"
if [ -e "$dest" ]; then
  echo "已存在：$dest" >&2
  exit 1
fi

pascal="$(printf '%s' "${domain:0:1}" | tr '[:lower:]' '[:upper:]')${domain:1}"

mkdir -p "$(dirname "$dest")"
cp -R scaffold/service-template "$dest"

# 先替换文件内容，再替换路径中的占位目录/文件名
find "$dest" -type f -print0 | while IFS= read -r -d '' f; do
  perl -pi -e "s/__DOMAIN_PASCAL__/${pascal}/g; s/__DOMAIN__/${domain}/g" "$f"
done
find "$dest" -depth -name '*__DOMAIN_PASCAL__*' -print0 | while IFS= read -r -d '' p; do
  mv "$p" "$(dirname "$p")/$(basename "$p" | sed "s/__DOMAIN_PASCAL__/${pascal}/g")"
done
find "$dest" -depth -name '*__DOMAIN__*' -print0 | while IFS= read -r -d '' p; do
  mv "$p" "$(dirname "$p")/$(basename "$p" | sed "s/__DOMAIN__/${domain}/g")"
done

include_line="include(\":services:${domain}-svc\")"
if ! grep -qF "$include_line" settings.gradle.kts; then
  printf '%s\n' "$include_line" >> settings.gradle.kts
  echo "已注册进 settings.gradle.kts：$include_line"
fi

echo "生成完成：${dest}（四层 + ArchitectureTest + Flyway 目录）"
