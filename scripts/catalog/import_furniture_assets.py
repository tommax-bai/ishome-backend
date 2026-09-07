#!/usr/bin/env -S uv run --quiet --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["psycopg[binary]", "python-ulid"]
# ///
"""家具品类闭集 + 家具资产尺寸表灌库（catalog 逻辑域，物理落 estate 库的 svc_catalog schema）。

唯一真源在契约仓，本脚本**读契约不复制契约**（同 verify_seeds.py 对 anchor_items 的取法）：
  registries/furniture_categories.json —— 21 个品类的闭集
  registries/furniture_assets.json     —— 25 行常规档位种子
迁移脚本里不写死这 25 行：写死等于把"契约仓那份改了怎么办"这条路堵上。改契约仓、重跑本脚本，
即重灌——按业务唯一键 upsert，重跑不重复成行（幂等）。

**不删行**：契约里没有的行留在表里不动。用户 2026-09-07 已拍"常规档位的行不删"——真实家具
覆盖不到的品类还要靠它兜底，删了求解当场取不到候选。表里多出来的行数在跑批末尾报出来，看得见。

用法：
  ./import_furniture_assets.py            # 真灌（要求 Flyway V1 已应用）
  ./import_furniture_assets.py --validate # 一次性影子 schema 内建表→灌库→统计→ROLLBACK，不留状态
连接：ISHOME_DB_* 环境变量，默认本地 ishome-dev（localhost:15432/ishome）。
契约检出：ISHOME_CONTRACTS_PATH，默认 backend 同级的 ../ishome-contracts。
目标 schema：ISHOME_CATALOG_SCHEMA，默认 svc_catalog（回归用它指到一次性 schema）。
"""
from __future__ import annotations
import glob
import json
import os
import re
import sys

import psycopg
from ulid import ULID

HERE = os.path.dirname(os.path.abspath(__file__))
MIGRATION_GLOB = os.path.join(
    HERE, "..", "..", "services/estate-svc/src/main/resources/db/migration/V*__*.sql")
SCHEMA = os.environ.get("ISHOME_CATALOG_SCHEMA", "svc_catalog")
VALIDATE_SCHEMA = "svc_catalog_validate"
"""--validate 走一次性影子 schema：正式 schema 已被 Flyway 建好后再往里 CREATE TABLE 必然撞表。
影子 schema 内建表→灌库→统计→ROLLBACK，DDL 在 PG 里同属事务，连 schema 本身都不会留下。"""

CATEGORIES_REL = "registries/furniture_categories.json"
ASSETS_REL = "registries/furniture_assets.json"
# 检出路径同 shared/contracts 模块与 verify_seeds.py 的约定
CONTRACTS_CANDIDATES = [
    os.environ.get("ISHOME_CONTRACTS_PATH"),
    os.path.join(HERE, "..", "..", "..", "ishome-contracts"),
    os.path.join(HERE, "..", "..", "contracts-checkout"),
]


def contract_json(rel: str):
    """读契约检出里的一份机器可读注册表。找不到即**响亮失败**——静默跳过等于灌了一批没有真源的数。"""
    for base in CONTRACTS_CANDIDATES:
        if not base:
            continue
        path = os.path.join(base, rel)
        if os.path.isfile(path):
            with open(path, encoding="utf-8") as f:
                return json.load(f)
    print(f"== 找不到 {rel}：clone ishome-contracts 到 backend 同级目录，"
          f"或设 ISHOME_CONTRACTS_PATH=<检出路径>（试过：{[c for c in CONTRACTS_CANDIDATES if c]}）")
    sys.exit(1)


def migration_sql(schema: str) -> str:
    """按版本号顺序拼接 catalog 相关迁移，占位符替换为目标 schema。

    "相关"按**内容**判（迁移体内出现 ${catalog_schema} 占位符），不按文件名判——同 import_seeds.py
    踩过的坑：靠文件名挑，一个名字里没那个词的增量迁移就会被影子 schema 漏建。
    """
    files = sorted(glob.glob(MIGRATION_GLOB),
                   key=lambda p: int(os.path.basename(p).split("__")[0][1:]))
    bodies = [open(f, encoding="utf-8").read() for f in files]
    return "\n".join(body.replace("${catalog_schema}", schema)
                     for body in bodies if "${catalog_schema}" in body)


def dsn() -> str:
    return (f"host={os.environ.get('ISHOME_DB_HOST', 'localhost')} "
            f"port={os.environ.get('ISHOME_DB_PORT', '15432')} "
            f"dbname={os.environ.get('ISHOME_DB_NAME', 'ishome')} "
            f"user={os.environ.get('ISHOME_DB_USER', 'ishome')} "
            f"password={os.environ.get('ISHOME_DB_PASSWORD', 'ishome-local-dev')}")


def check_contracts(categories: dict, assets: list) -> None:
    """灌之前先核对两份契约自己对不对得上。

    库里有外键与 CHECK 兜底，这里仍然拦一道：报错要说得出**哪一行、哪个字段、错在哪**，
    而不是让调用者去读一句 PG 的约束名。核验不过一行都不灌（拒灌，不半灌）。
    """
    category_pattern = re.compile(r"^[a-z][a-z0-9-]*$")
    asset_id_pattern = re.compile(r"^asset-[a-z0-9][a-z0-9-]*$")
    semantic_id_pattern = re.compile(r"^asset-.*[a-z]")
    tiers = {"small", "standard", "large"}
    errors: list[str] = []

    for name in categories:
        if not category_pattern.match(name):
            errors.append(f"品类词形非法（ASCII 小写 kebab-case）：{name}")

    seen: set[tuple[str, str]] = set()
    for row in assets:
        aid = row.get("asset_id", "<缺 asset_id>")
        if row["category"] not in categories:
            errors.append(f"{aid}：品类 {row['category']} 不在闭集内——表外拒收")
        if row["size_tier"] not in tiers:
            errors.append(f"{aid}：档位 {row['size_tier']} 不在 small/standard/large 内")
        if not asset_id_pattern.match(aid):
            errors.append(f"{aid}：asset_id 形态非法（前缀 asset- 即命名空间）")
        elif not semantic_id_pattern.search(aid):
            # 契约的 asset_id_pattern 只管字符集，asset-001 照样过得去；**命名禁纯序号**是红线，
            # 可机检的判据 = asset- 之后至少有一个字母（DB 侧同一条落在列 CHECK 上）
            errors.append(f"{aid}：asset_id 是纯序号——命名禁纯序号，要语义命名")
        for dim in ("width_m", "depth_m", "height_m"):
            value = row.get(dim)
            if not isinstance(value, (int, float)) or isinstance(value, bool) or value <= 0:
                errors.append(f"{aid}：{dim} 必须是正的点值（不存区间），实得 {value!r}")
        if not str(row.get("provenance") or "").strip():
            errors.append(f"{aid}：来路栏空着——没定源要如实写「无定源」，不许省掉这一栏")
        key = (row["category"], row["size_tier"])
        if key in seen:
            errors.append(f"{aid}：(category, size_tier) = {key} 在契约里出现了两次")
        seen.add(key)

    if errors:
        for e in errors:
            print(f"ERROR {e}")
        print(f"== 契约核验未过（{len(errors)} 条），拒绝灌库")
        sys.exit(1)


def main() -> None:
    validate = "--validate" in sys.argv
    schema = VALIDATE_SCHEMA if validate else SCHEMA

    categories = contract_json(CATEGORIES_REL)["categories"]
    assets = contract_json(ASSETS_REL)["assets"]
    check_contracts(categories, assets)

    with psycopg.connect(dsn()) as conn, conn.cursor() as cur:
        if validate:
            # 迁移体自带 CREATE SCHEMA IF NOT EXISTS，影子 schema 连同表一起在事务里建
            cur.execute(migration_sql(schema))

        for name, meta in categories.items():
            cur.execute(
                f"INSERT INTO {schema}.furniture_categories "
                "(id, category, semantics, aliases, first_seen, layout_use) "
                "VALUES (%s, %s, %s, %s::jsonb, %s, %s) "
                "ON CONFLICT (category) DO UPDATE SET "
                "semantics = EXCLUDED.semantics, aliases = EXCLUDED.aliases, "
                "first_seen = EXCLUDED.first_seen, layout_use = EXCLUDED.layout_use, "
                "updated_at = now()",
                (str(ULID()), name, meta["semantics"],
                 json.dumps(meta.get("aliases", []), ensure_ascii=False),
                 meta.get("first_seen"), meta.get("layout_use")))

        for row in assets:
            # 取行键是 (category, size_tier)——求解按这两样取行，故冲突也认这两样。
            # asset_id 另有唯一约束：契约里把一个已发布的 asset_id 挪到别的品类/档位上，
            # 这里会撞约束报出来，不会静默改掉——已发布的 asset_id 只增不改。
            cur.execute(
                f"INSERT INTO {schema}.furniture_assets "
                "(id, asset_id, category, size_tier, width_m, depth_m, height_m, "
                " sku_ref, size_source, provenance) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s) "
                "ON CONFLICT (category, size_tier) DO UPDATE SET "
                "asset_id = EXCLUDED.asset_id, width_m = EXCLUDED.width_m, "
                "depth_m = EXCLUDED.depth_m, height_m = EXCLUDED.height_m, "
                "sku_ref = EXCLUDED.sku_ref, size_source = EXCLUDED.size_source, "
                "provenance = EXCLUDED.provenance, updated_at = now()",
                (str(ULID()), row["asset_id"], row["category"], row["size_tier"],
                 row["width_m"], row["depth_m"], row["height_m"],
                 row.get("sku_ref"), row["size_source"], row["provenance"]))

        cur.execute(f"SELECT count(*) FROM {schema}.furniture_categories")
        categories_in_table = cur.fetchone()[0]
        cur.execute(f"SELECT count(*) FROM {schema}.furniture_assets")
        assets_in_table = cur.fetchone()[0]
        # 没定源不因为入库而消失：来路栏里写着「无定源」的行照实点出来
        cur.execute(
            f"SELECT count(*) FROM {schema}.furniture_assets WHERE provenance LIKE '%无定源%'")
        no_source_rows = cur.fetchone()[0]

        print(f"  schema: {schema}")
        print(f"  furniture_categories: 契约 {len(categories)} / 表内 {categories_in_table}")
        print(f"  furniture_assets:     契约 {len(assets)} / 表内 {assets_in_table}")
        print(f"  其中来路为「无定源」: {no_source_rows}")
        if categories_in_table > len(categories) or assets_in_table > len(assets):
            print("  注：表内多出来的行是契约里已经没有的，**本脚本不删**（常规档位的行不删，"
                  "真实家具覆盖不到的品类还要靠它兜底）")

        if validate:
            conn.rollback()
            print("== --validate：已回滚，未留任何状态")
        else:
            conn.commit()
            print("== 已提交。真源仍在契约仓，改了重跑本脚本即重灌")


if __name__ == "__main__":
    main()
