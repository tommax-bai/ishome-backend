#!/usr/bin/env -S uv run --quiet --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["psycopg[binary]", "python-ulid"]
# ///
"""域级 release 发布（规范 v2.2 规则 4.12：工作态与运行态分离，运行时求值只读 release 快照）。

发布动作 = 把该域当前工作态资产固化为一个**不可变**版本（release_tag = {domain}@v{n}）：
- 快照含该域全部未删条目（各 asset_id 取最新 version）并带 calibration 标记——draft 条目随包
  只为降档呈现（规则 4.10：draft 禁入 PAID，只能降档如参考级区间；PAID 门禁在求值线执行）；
- `_common`（domain=cross）编译期资产**物化**进每个域的快照——release 自包含、可独立回滚
  （rulebook-seeds/README 裁决），代价是跨域判据在各 release 各存一份；
- 只 INSERT 不 UPDATE：回滚=切回旧 release_tag；
- 幂等：与该域最新 release 内容一致（不含时间戳）则跳过，不空切版本。

用法：
  ./publish_release.py lighting ergonomics budget   # 发布指定域
  ./publish_release.py --all                        # 六知识域全发（dom- 注册表打 ✓ 的域）
连接：ISHOME_DB_* 环境变量，默认本地 ishome-dev（localhost:15432/ishome）。
"""
from __future__ import annotations
import json, os, re, sys, psycopg
from psycopg.types.json import Json
from ulid import ULID

# dom- 注册表中"知识域(release)"打 ✓ 的六域（contracts rulebook/README.md §1；存储用去前缀形态）
KNOWLEDGE_DOMAINS = ["budget", "ergonomics", "lighting", "material", "softdeco", "storage"]
FORMS = ["rules", "parameters", "attributes", "templates", "vocabularies", "personas", "checks"]


def dsn():
    return (f"host={os.environ.get('ISHOME_DB_HOST','localhost')} "
            f"port={os.environ.get('ISHOME_DB_PORT','15432')} "
            f"dbname={os.environ.get('ISHOME_DB_NAME','ishome')} "
            f"user={os.environ.get('ISHOME_DB_USER','ishome')} "
            f"password={os.environ.get('ISHOME_DB_PASSWORD','ishome-local-dev')}")


# 有外部真源、受交叉验证约束的三形态（规则 4.16）——它们才有 conflict 列
CONFLICT_AWARE = {"rules", "parameters", "attributes"}


def snapshot_assets(cur, domain: str) -> tuple[dict, list[str]]:
    """各形态取 (domain, cross) 未删条目的最新 version，按 asset_id 排序——确定性快照。

    行内容剔除 id/created_at/updated_at/deleted_at：快照比较基于内容，不受导入时间戳影响。
    **conflict 条目排除出快照**（规则 4.16①"不一致挂 conflict 且不进 release"）：与 draft 的
    区别在于降不降得动——draft 是依据不足可降档呈现，conflict 是两个源各说各话，参考形态呈现的
    仍是一个没有共识的数。被排除者返回清单，发布时打印，避免"悄悄少了一条"。
    """
    assets, excluded = {}, []
    for form in FORMS:
        conflict_filter = "AND conflict IS NOT TRUE " if form in CONFLICT_AWARE else ""
        cur.execute(
            f"SELECT to_jsonb(t) - 'id' - 'created_at' - 'updated_at' - 'deleted_at' "
            f"FROM (SELECT DISTINCT ON (asset_id) * FROM svc_rulebook.{form} "
            f"      WHERE domain IN (%s, 'cross') AND deleted_at IS NULL {conflict_filter}"
            f"      ORDER BY asset_id, version DESC) t ORDER BY t.asset_id",
            (domain,))
        assets[form] = [r[0] for r in cur.fetchall()]
        if conflict_filter:
            cur.execute(
                f"SELECT DISTINCT asset_id FROM svc_rulebook.{form} "
                f"WHERE domain IN (%s, 'cross') AND deleted_at IS NULL AND conflict IS TRUE "
                f"ORDER BY asset_id", (domain,))
            excluded += [r[0] for r in cur.fetchall()]
    return assets, sorted(excluded)


def latest_release(cur, domain: str) -> tuple[int, dict | None]:
    cur.execute("SELECT release_tag, snapshot FROM svc_rulebook.releases WHERE domain=%s", (domain,))
    best_n, best_snap = 0, None
    for tag, snap in cur.fetchall():
        m = re.fullmatch(rf"{re.escape(domain)}@v(\d+)", tag)
        if m and int(m.group(1)) > best_n:
            best_n, best_snap = int(m.group(1)), snap
    return best_n, best_snap


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    domains = KNOWLEDGE_DOMAINS if "--all" in sys.argv else args
    if not domains:
        print(__doc__); sys.exit(2)
    unknown = [d for d in domains if d not in KNOWLEDGE_DOMAINS]
    if unknown:
        print(f"== 非知识域，无 release 发布粒度：{unknown}（dom- 注册表打 ✓ 的域：{KNOWLEDGE_DOMAINS}）")
        sys.exit(2)
    with psycopg.connect(dsn()) as conn, conn.cursor() as cur:
        for domain in domains:
            assets, excluded = snapshot_assets(cur, domain)
            n, prev = latest_release(cur, domain)
            if prev is not None and prev.get("assets") == assets:
                print(f"  skip {domain}@v{n}（内容未变，不空切版本）")
                continue
            tag = f"{domain}@v{n + 1}"
            counts = {f: len(v) for f, v in assets.items() if v}
            calibrated = sum(1 for f in FORMS for a in assets[f]
                             if a.get("calibration") == "calibrated")
            snap = {"release_tag": tag, "domain": domain,
                    "stats": {**counts, "calibrated": calibrated, "excluded_conflict": excluded},
                    "assets": assets}
            cur.execute(
                "INSERT INTO svc_rulebook.releases (id, domain, release_tag, snapshot) "
                "VALUES (%s, %s, %s, %s)", (str(ULID()), domain, tag, Json(snap)))
            print(f"  cut  {tag}：{counts}，calibrated={calibrated}"
                  + (f"，conflict 排除 {len(excluded)}：{excluded}" if excluded else ""))
        conn.commit()
    print("== release 为不可变快照：回滚=切回旧 release_tag，禁止 UPDATE（规则 4.12）")


if __name__ == "__main__":
    main()
