#!/usr/bin/env -S uv run --quiet --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["psycopg[binary]"]
# ///
"""灌库跑批自己的回归（家具资产尺寸表）。

黑盒跑真脚本：建一个一次性 schema，用 ISHOME_CATALOG_SCHEMA 指过去，ISHOME_CONTRACTS_PATH 指到
临时改过的契约副本，断言"该灌的灌了、该拒的拒了、重跑不重复成行"。为什么不 import 函数单测：
import_furniture_assets.py 是带 uv 头的单文件脚本，跑法就是被直接执行——测它被执行时的行为，
才是测真正上线的那条路径（同 test_verify_seeds.py 的取法）。

本地 PG（默认 localhost:15432）不可达即跳过，退出码 0——同 Java 侧 EnabledIfLocalPostgres 的口径。

用法：./test_import_furniture_assets.py   （退出码 0 = 全绿）
"""
from __future__ import annotations
import copy
import glob
import json
import os
import shutil
import socket
import subprocess
import sys
import tempfile

import psycopg

HERE = os.path.dirname(os.path.abspath(__file__))
IMPORTER = os.path.join(HERE, "import_furniture_assets.py")
MIGRATION_GLOB = os.path.join(
    HERE, "..", "..", "services/estate-svc/src/main/resources/db/migration/V*__*.sql")
SCHEMA = "svc_catalog_regression"
CONTRACTS = os.environ.get("ISHOME_CONTRACTS_PATH") or os.path.join(
    HERE, "..", "..", "..", "ishome-contracts")

failures: list[str] = []


def dsn() -> str:
    return (f"host={os.environ.get('ISHOME_DB_HOST', 'localhost')} "
            f"port={os.environ.get('ISHOME_DB_PORT', '15432')} "
            f"dbname={os.environ.get('ISHOME_DB_NAME', 'ishome')} "
            f"user={os.environ.get('ISHOME_DB_USER', 'ishome')} "
            f"password={os.environ.get('ISHOME_DB_PASSWORD', 'ishome-local-dev')}")


def postgres_reachable() -> bool:
    host = os.environ.get("ISHOME_DB_HOST", "localhost")
    port = int(os.environ.get("ISHOME_DB_PORT", "15432"))
    try:
        with socket.create_connection((host, port), timeout=0.5):
            return True
    except OSError:
        return False


def migration_sql(schema: str) -> str:
    """按内容挑 catalog 相关迁移（同灌库脚本），拼成建 schema 的 SQL。"""
    files = sorted(glob.glob(MIGRATION_GLOB),
                   key=lambda p: int(os.path.basename(p).split("__")[0][1:]))
    bodies = [open(f, encoding="utf-8").read() for f in files]
    return "\n".join(body.replace("${catalog_schema}", schema)
                     for body in bodies if "${catalog_schema}" in body)


def reset_schema() -> None:
    with psycopg.connect(dsn(), autocommit=True) as conn, conn.cursor() as cur:
        cur.execute(f"DROP SCHEMA IF EXISTS {SCHEMA} CASCADE")
        cur.execute(migration_sql(SCHEMA))


def drop_schema() -> None:
    with psycopg.connect(dsn(), autocommit=True) as conn, conn.cursor() as cur:
        cur.execute(f"DROP SCHEMA IF EXISTS {SCHEMA} CASCADE")


def query(sql: str):
    with psycopg.connect(dsn()) as conn, conn.cursor() as cur:
        cur.execute(sql)
        return cur.fetchall()


def contracts_copy(target: str, mutate=None) -> str:
    """把真契约拷一份到临时目录，可选地改一改，返回可当 ISHOME_CONTRACTS_PATH 用的根。"""
    registries = os.path.join(target, "registries")
    os.makedirs(registries, exist_ok=True)
    for name in ("furniture_categories.json", "furniture_assets.json"):
        shutil.copy(os.path.join(CONTRACTS, "registries", name),
                    os.path.join(registries, name))
    if mutate:
        assets_path = os.path.join(registries, "furniture_assets.json")
        with open(assets_path, encoding="utf-8") as f:
            payload = json.load(f)
        mutate(payload)
        with open(assets_path, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False)
    return target


def run_importer(contracts_path: str) -> subprocess.CompletedProcess:
    env = {**os.environ,
           "ISHOME_CATALOG_SCHEMA": SCHEMA,
           "ISHOME_CONTRACTS_PATH": contracts_path}
    return subprocess.run([IMPORTER], capture_output=True, text=True, env=env)


def check(name: str, condition: bool, detail: str = "") -> None:
    if not condition:
        failures.append(f"{name}{': ' + detail if detail else ''}")
    print(f"  {'ok  ' if condition else 'FAIL'} {name}")


def main() -> None:
    if not postgres_reachable():
        print("== 本地 PG 不可达，跳过灌库回归（同 EnabledIfLocalPostgres 口径）")
        return

    reset_schema()
    try:
        with tempfile.TemporaryDirectory() as tmp:
            plain = contracts_copy(os.path.join(tmp, "plain"))

            # ① 灌得进去：21 个品类 + 25 行资产
            first = run_importer(plain)
            check("首灌退出码 0", first.returncode == 0, first.stdout + first.stderr)
            counts = query(f"SELECT (SELECT count(*) FROM {SCHEMA}.furniture_categories), "
                           f"(SELECT count(*) FROM {SCHEMA}.furniture_assets)")[0]
            check("首灌行数 = 契约 21 品类 / 25 资产", counts == (21, 25), str(counts))

            # ② 幂等：同一份契约再灌一遍，不重复成行
            second = run_importer(plain)
            check("重灌退出码 0", second.returncode == 0, second.stdout + second.stderr)
            counts = query(f"SELECT (SELECT count(*) FROM {SCHEMA}.furniture_categories), "
                           f"(SELECT count(*) FROM {SCHEMA}.furniture_assets)")[0]
            check("重灌行数不变（幂等）", counts == (21, 25), str(counts))

            # ③ 来路栏原样存住：7 行「无定源」一个字不改
            no_source = query(
                f"SELECT count(*) FROM {SCHEMA}.furniture_assets "
                "WHERE provenance LIKE '%无定源%'")[0][0]
            check("「无定源」7 行原样存住", no_source == 7, str(no_source))
            nightstand = query(
                f"SELECT provenance FROM {SCHEMA}.furniture_assets "
                "WHERE asset_id = 'asset-nightstand-standard'")[0][0]
            check("无定源那句话逐字存住（不折进 size_source）",
                  "无定源" in nightstand, nightstand)

            # ④ 契约改了能重新灌：改一个尺寸，重跑即落到表上
            def widen_bed(payload):
                for row in payload["assets"]:
                    if row["asset_id"] == "asset-bed-large":
                        row["width_m"] = 1.9

            changed = contracts_copy(os.path.join(tmp, "changed"), widen_bed)
            third = run_importer(changed)
            check("改过的契约灌得进去", third.returncode == 0, third.stdout + third.stderr)
            width = query(f"SELECT width_m FROM {SCHEMA}.furniture_assets "
                          "WHERE asset_id = 'asset-bed-large'")[0][0]
            check("契约改了值就跟着改（同一行，不新增）", width == 1.9, str(width))
            total = query(f"SELECT count(*) FROM {SCHEMA}.furniture_assets")[0][0]
            check("改值不新增行", total == 25, str(total))

            # ⑤ 闭集外的品类拒灌，且一行都不动（拒灌不半灌）
            def add_outsider(payload):
                outsider = copy.deepcopy(payload["assets"][0])
                outsider.update(asset_id="asset-hammock-standard", category="hammock",
                                size_tier="standard")
                payload["assets"].append(outsider)

            outside = contracts_copy(os.path.join(tmp, "outside"), add_outsider)
            fourth = run_importer(outside)
            check("闭集外品类拒灌（退出码非 0）", fourth.returncode != 0, fourth.stdout)
            check("拒灌时说得出是哪一行错在哪",
                  "hammock" in fourth.stdout and "不在闭集内" in fourth.stdout, fourth.stdout)
            total = query(f"SELECT count(*) FROM {SCHEMA}.furniture_assets")[0][0]
            check("拒灌不半灌（表内仍是 25 行）", total == 25, str(total))

            # ⑥ 纯序号 asset_id 拒灌（命名禁纯序号，红线在灌库这一层也拦）
            def renumber(payload):
                payload["assets"][0]["asset_id"] = "asset-001"

            numbered = contracts_copy(os.path.join(tmp, "numbered"), renumber)
            fifth = run_importer(numbered)
            check("纯序号 asset_id 拒灌", fifth.returncode != 0, fifth.stdout)
    finally:
        drop_schema()

    print()
    if failures:
        for f in failures:
            print("FAIL", f)
        print(f"== 灌库回归：{len(failures)} 条不过")
        sys.exit(1)
    print("== 灌库回归：全绿")


if __name__ == "__main__":
    main()
