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
REGISTRIES = ("furniture_categories.json", "furniture_assets.json")

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
    """把真契约拷一份到临时目录，可选地改一改，返回可当 ISHOME_CONTRACTS_PATH 用的根。

    mutate 收两份 payload（品类、资产），就地改——"退役"这类用例两份要一起改才说得清楚。
    """
    registries = os.path.join(target, "registries")
    os.makedirs(registries, exist_ok=True)
    payloads = {}
    for name in REGISTRIES:
        with open(os.path.join(CONTRACTS, "registries", name), encoding="utf-8") as f:
            payloads[name] = json.load(f)
    if mutate:
        mutate(payloads["furniture_categories.json"]["categories"],
               payloads["furniture_assets.json"])
    for name in REGISTRIES:
        with open(os.path.join(registries, name), "w", encoding="utf-8") as f:
            json.dump(payloads[name], f, ensure_ascii=False)
    return target


def run_importer(contracts_path: str) -> subprocess.CompletedProcess:
    env = {**os.environ,
           "ISHOME_CATALOG_SCHEMA": SCHEMA,
           "ISHOME_CONTRACTS_PATH": contracts_path}
    return subprocess.run([IMPORTER], capture_output=True, text=True, env=env)


def counts() -> tuple[int, int]:
    return query(f"SELECT (SELECT count(*) FROM {SCHEMA}.furniture_categories), "
                 f"(SELECT count(*) FROM {SCHEMA}.furniture_assets)")[0]


def asset_row(assets, asset_id):
    return next(row for row in assets["assets"] if row["asset_id"] == asset_id)


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

            # ① 灌得进去：21 个品类（含 1 个已退役）+ 24 行资产
            first = run_importer(plain)
            check("首灌退出码 0", first.returncode == 0, first.stdout + first.stderr)
            check("首灌行数 = 契约 21 品类 / 24 资产", counts() == (21, 24), str(counts()))

            # ② 幂等：同一份契约再灌一遍，不重复成行
            second = run_importer(plain)
            check("重灌退出码 0", second.returncode == 0, second.stdout + second.stderr)
            check("重灌行数不变（幂等）", counts() == (21, 24), str(counts()))

            # ③ 整数毫米：值逐字落到表上（1.8 m → 1800 mm）
            bed = query(f"SELECT width_mm, depth_mm, height_mm FROM {SCHEMA}.furniture_assets "
                        "WHERE asset_id = 'asset-bed-large'")[0]
            check("尺寸是整数毫米、值对得上契约", bed == (1800, 2000, 450), str(bed))
            bad_dims = query(f"SELECT count(*) FROM {SCHEMA}.furniture_assets "
                             "WHERE width_mm <= 0 OR depth_mm <= 0 OR height_mm <= 0")[0][0]
            check("24 行尺寸全是正整数毫米", bad_dims == 0, str(bad_dims))

            # ④ 退役：词留着、行不留（用户裁决 2026-09-07 卫浴拆开）
            retired = query(f"SELECT retired IS NOT NULL, is_retired "
                            f"FROM {SCHEMA}.furniture_categories "
                            "WHERE category = 'bathroom-fixture'")
            check("退役品类的词留在闭集里（只增不改）", retired == [(True, True)], str(retired))
            rows = query(f"SELECT count(*) FROM {SCHEMA}.furniture_assets "
                         "WHERE category = 'bathroom-fixture'")[0][0]
            check("退役品类零资产行", rows == 0, str(rows))
            replacements = query(f"SELECT count(*) FROM {SCHEMA}.furniture_assets "
                                 "WHERE category IN ('toilet', 'vanity', 'shower')")[0][0]
            check("替代的三件都在表里", replacements == 3, str(replacements))

            # ⑤ 来路栏原样存住：7 行「无定源」一个字不改
            no_source = query(
                f"SELECT count(*) FROM {SCHEMA}.furniture_assets "
                "WHERE provenance LIKE '%无定源%'")[0][0]
            check("「无定源」7 行原样存住", no_source == 7, str(no_source))
            nightstand = query(
                f"SELECT provenance FROM {SCHEMA}.furniture_assets "
                "WHERE asset_id = 'asset-nightstand-standard'")[0][0]
            check("无定源那句话逐字存住（不折进 size_source）",
                  "无定源" in nightstand, nightstand)

            # ⑥ 契约改了能重新灌：改一个尺寸，重跑即落到表上
            def widen_bed(cats, assets):
                asset_row(assets, "asset-bed-large")["width_mm"] = 1900

            changed = contracts_copy(os.path.join(tmp, "changed"), widen_bed)
            third = run_importer(changed)
            check("改过的契约灌得进去", third.returncode == 0, third.stdout + third.stderr)
            width = query(f"SELECT width_mm FROM {SCHEMA}.furniture_assets "
                          "WHERE asset_id = 'asset-bed-large'")[0][0]
            check("契约改了值就跟着改（同一行，不新增）", width == 1900, str(width))
            check("改值不新增行", counts() == (21, 24), str(counts()))

            # ⑦ 退役这件事本身灌得动：先灌一份"卫浴还没退役、还带着行"的旧契约（25 行），
            #    再灌真契约——那一行随退役被撤下，词还在
            def unretire_bathroom(cats, assets):
                cats["bathroom-fixture"].pop("retired", None)
                merged = copy.deepcopy(asset_row(assets, "asset-toilet-standard"))
                merged.update(asset_id="asset-bathroom-fixture-standard",
                              category="bathroom-fixture", width_mm=1200,
                              depth_mm=500, height_mm=850)
                assets["assets"].append(merged)

            old = contracts_copy(os.path.join(tmp, "old"), unretire_bathroom)
            fourth = run_importer(old)
            check("退役前那版契约灌得进去（25 行）",
                  fourth.returncode == 0 and counts() == (21, 25),
                  f"{fourth.stdout}{fourth.stderr} counts={counts()}")
            fifth = run_importer(plain)
            check("再灌真契约：退役灌得动（不被外键卡住）", fifth.returncode == 0,
                  fifth.stdout + fifth.stderr)
            check("退役随即撤下那一行（25 → 24）", counts() == (21, 24), str(counts()))
            check("撤行这件事跑批里报得出来",
                  "随品类退役撤下的资产行: 1" in fifth.stdout, fifth.stdout)

            # ⑧ 不变量一：契约里退役品类还带着资产行 → 拒灌
            def retired_with_row(cats, assets):
                merged = copy.deepcopy(asset_row(assets, "asset-toilet-standard"))
                merged.update(asset_id="asset-bathroom-fixture-standard",
                              category="bathroom-fixture")
                assets["assets"].append(merged)

            bad_retired = contracts_copy(os.path.join(tmp, "bad-retired"), retired_with_row)
            sixth = run_importer(bad_retired)
            check("退役品类却有资产行 → 拒灌", sixth.returncode != 0, sixth.stdout)
            check("拒灌时说得出是哪个品类",
                  "bathroom-fixture" in sixth.stdout and "已退役却还有资产行" in sixth.stdout,
                  sixth.stdout)

            # ⑨ 不变量二：在役品类一行资产都没有 → 拒灌
            def drop_washer(cats, assets):
                assets["assets"] = [r for r in assets["assets"] if r["category"] != "washer"]

            missing = contracts_copy(os.path.join(tmp, "missing"), drop_washer)
            seventh = run_importer(missing)
            check("在役品类没有资产行 → 拒灌", seventh.returncode != 0, seventh.stdout)
            check("拒灌时说得出是哪个品类",
                  "washer" in seventh.stdout and "一行资产都没有" in seventh.stdout,
                  seventh.stdout)

            # ⑩ 闭集外的品类拒灌，且一行都不动（拒灌不半灌）
            def add_outsider(cats, assets):
                outsider = copy.deepcopy(assets["assets"][0])
                outsider.update(asset_id="asset-hammock-standard", category="hammock",
                                size_tier="standard")
                assets["assets"].append(outsider)

            outside = contracts_copy(os.path.join(tmp, "outside"), add_outsider)
            eighth = run_importer(outside)
            check("闭集外品类拒灌（退出码非 0）", eighth.returncode != 0, eighth.stdout)
            check("拒灌时说得出是哪一行错在哪",
                  "hammock" in eighth.stdout and "不在闭集内" in eighth.stdout, eighth.stdout)
            check("拒灌不半灌（表内仍是 24 行）", counts() == (21, 24), str(counts()))

            # ⑪ 非整数毫米拒灌：1.85 只可能是"米忘了换算"或一个没有来源的半毫米精度
            def metres_left_over(cats, assets):
                asset_row(assets, "asset-fridge-standard")["height_mm"] = 1.85

            metric = contracts_copy(os.path.join(tmp, "metric"), metres_left_over)
            ninth = run_importer(metric)
            check("非整数毫米拒灌", ninth.returncode != 0, ninth.stdout)
            check("拒灌时点名是哪个字段",
                  "height_mm" in ninth.stdout and "正整数毫米" in ninth.stdout, ninth.stdout)

            # ⑫ 纯序号 asset_id 拒灌（命名禁纯序号，红线在灌库这一层也拦）
            def renumber(cats, assets):
                assets["assets"][0]["asset_id"] = "asset-001"

            numbered = contracts_copy(os.path.join(tmp, "numbered"), renumber)
            tenth = run_importer(numbered)
            check("纯序号 asset_id 拒灌", tenth.returncode != 0, tenth.stdout)
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
