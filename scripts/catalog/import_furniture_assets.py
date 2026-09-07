#!/usr/bin/env -S uv run --quiet --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["psycopg[binary]", "python-ulid"]
# ///
"""家具品类闭集 + 家具资产尺寸表灌库（catalog 逻辑域，物理落 estate 库的 svc_catalog schema）。

唯一真源在契约仓，本脚本**读契约不复制契约**（同 verify_seeds.py 对 anchor_items 的取法）：
  registries/furniture_categories.json —— 21 个品类的闭集（未退役 20 + 退役 1）
  registries/furniture_assets.json     —— 24 行常规档位种子，整数毫米
迁移脚本里不写死这 24 行：写死等于把"契约仓那份改了怎么办"这条路堵上。改契约仓、重跑本脚本，
即重灌——按业务唯一键 upsert，重跑不重复成行（幂等）。

**不删行**：契约里没有的行留在表里不动。用户 2026-09-07 已拍"常规档位的行不删"——真实家具
覆盖不到的品类还要靠它兜底，删了求解当场取不到候选。表里多出来的行数在跑批末尾报出来，看得见。
**删行的例外只有一种＝品类退役**（同日裁决"卫浴在资产库里拆开"把 bathroom-fixture 那个粒度废掉）：
契约把品类标了 retired，本脚本连同它的资产行一起撤下并报出撤了几行——退役品类零资产行是
库里的结构事实（复合外键），撤行这一步必须在灌品类之前做，否则外键当场拒掉那次退役。

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

    for name, meta in categories.items():
        if not category_pattern.match(name):
            errors.append(f"品类词形非法（ASCII 小写 kebab-case）：{name}")
        retired = meta.get("retired")
        if retired is not None and not str(retired).strip():
            errors.append(f"{name}：退役告示是空的——退役要说清退到哪几条，不能只说废了")

    retired_names = {name for name, meta in categories.items() if meta.get("retired")}
    categories_with_rows = {row.get("category") for row in assets}
    # 不变量一：退役品类零资产行（退役的词不许再进 candidates[]）
    for name in sorted(retired_names & categories_with_rows):
        errors.append(f"{name}：品类已退役却还有资产行——退役即撤行，退役的词不许再进求解")
    # 不变量二：未退役品类均有行（在役却取不到候选＝求解当场摆不出这件家具）
    for name in sorted(set(categories) - retired_names - categories_with_rows):
        errors.append(f"{name}：在役品类一行资产都没有——求解拿它取候选会当场取空")

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
        for dim in ("width_mm", "depth_mm", "height_mm"):
            value = row.get(dim)
            # 整数毫米（用户裁决 2026-09-07"量纲统一毫米，家具资产表不例外"）：
            # 浮点毫米一律拒——1.85 这种值只可能是"米忘了换算"或者一个没有来源的半毫米精度
            if not isinstance(value, int) or isinstance(value, bool) or value <= 0:
                errors.append(f"{aid}：{dim} 必须是正整数毫米（点值不存区间），实得 {value!r}")
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

        # ① 先撤退役品类的资产行。顺序不能反：库里"退役品类零资产行"是复合外键的结构事实，
        # 先去把品类标成退役，外键会当场拒掉那次 UPDATE（它还被资产行引用着）。
        # 这是**唯一**允许删行的路，且由契约里的退役标记授权，不是脚本自己决定删谁。
        retired_names = sorted(name for name, meta in categories.items() if meta.get("retired"))
        withdrawn = 0
        for name in retired_names:
            cur.execute(f"DELETE FROM {schema}.furniture_assets WHERE category = %s", (name,))
            withdrawn += cur.rowcount

        # ② 灌品类闭集（含退役告示）
        for name, meta in categories.items():
            cur.execute(
                f"INSERT INTO {schema}.furniture_categories "
                "(id, category, semantics, aliases, retired, first_seen, layout_use) "
                "VALUES (%s, %s, %s, %s::jsonb, %s, %s, %s) "
                "ON CONFLICT (category) DO UPDATE SET "
                "semantics = EXCLUDED.semantics, aliases = EXCLUDED.aliases, "
                "retired = EXCLUDED.retired, first_seen = EXCLUDED.first_seen, "
                "layout_use = EXCLUDED.layout_use, updated_at = now()",
                (str(ULID()), name, meta["semantics"],
                 json.dumps(meta.get("aliases", []), ensure_ascii=False),
                 meta.get("retired"), meta.get("first_seen"), meta.get("layout_use")))

        # ③ 灌资产行
        for row in assets:
            # 取行键是 (category, size_tier)——求解按这两样取行，故冲突也认这两样。
            # asset_id 另有唯一约束：契约里把一个已发布的 asset_id 挪到别的品类/档位上，
            # 这里会撞约束报出来，不会静默改掉——已发布的 asset_id 不改名、不复用。
            cur.execute(
                f"INSERT INTO {schema}.furniture_assets "
                "(id, asset_id, category, size_tier, width_mm, depth_mm, height_mm, "
                " sku_ref, size_source, provenance) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s) "
                "ON CONFLICT (category, size_tier) DO UPDATE SET "
                "asset_id = EXCLUDED.asset_id, width_mm = EXCLUDED.width_mm, "
                "depth_mm = EXCLUDED.depth_mm, height_mm = EXCLUDED.height_mm, "
                "sku_ref = EXCLUDED.sku_ref, size_source = EXCLUDED.size_source, "
                "provenance = EXCLUDED.provenance, updated_at = now()",
                (str(ULID()), row["asset_id"], row["category"], row["size_tier"],
                 row["width_mm"], row["depth_mm"], row["height_mm"],
                 row.get("sku_ref"), row["size_source"], row["provenance"]))

        # ④ 灌完对着表自己再验一遍两条不变量。契约核验拦的是"契约那份写错了"，这一遍拦的是
        # "表里还留着上一版的东西"——表里可能有契约里已经没有的行，只有查表才答得出。
        # 不过就不提交（拒灌不半灌）。
        cur.execute(
            f"SELECT a.category FROM {schema}.furniture_assets a "
            f"JOIN {schema}.furniture_categories c ON c.category = a.category "
            "WHERE c.retired IS NOT NULL GROUP BY a.category ORDER BY 1")
        retired_with_rows = [r[0] for r in cur.fetchall()]
        cur.execute(
            f"SELECT c.category FROM {schema}.furniture_categories c "
            f"WHERE c.retired IS NULL AND NOT EXISTS (SELECT 1 FROM {schema}.furniture_assets a "
            "WHERE a.category = c.category) ORDER BY 1")
        active_without_rows = [r[0] for r in cur.fetchall()]
        if retired_with_rows or active_without_rows:
            for name in retired_with_rows:
                print(f"ERROR 表内不变量破了：{name} 已退役却还有资产行")
            for name in active_without_rows:
                print(f"ERROR 表内不变量破了：{name} 在役却一行资产都没有")
            conn.rollback()
            print("== 灌后自检未过，已回滚，未留任何状态")
            sys.exit(1)

        cur.execute(f"SELECT count(*) FROM {schema}.furniture_categories")
        categories_in_table = cur.fetchone()[0]
        cur.execute(f"SELECT count(*) FROM {schema}.furniture_categories WHERE retired IS NOT NULL")
        retired_in_table = cur.fetchone()[0]
        cur.execute(f"SELECT count(*) FROM {schema}.furniture_assets")
        assets_in_table = cur.fetchone()[0]
        # 没定源不因为入库而消失：来路栏里写着「无定源」的行照实点出来
        cur.execute(
            f"SELECT count(*) FROM {schema}.furniture_assets WHERE provenance LIKE '%无定源%'")
        no_source_rows = cur.fetchone()[0]

        print(f"  schema: {schema}")
        print(f"  furniture_categories: 契约 {len(categories)} / 表内 {categories_in_table}"
              f"（其中已退役 {retired_in_table}）")
        print(f"  furniture_assets:     契约 {len(assets)} / 表内 {assets_in_table}")
        print(f"  其中来路为「无定源」: {no_source_rows}")
        if withdrawn:
            print(f"  随品类退役撤下的资产行: {withdrawn}（唯一允许删行的路，由契约的退役标记授权）")
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
