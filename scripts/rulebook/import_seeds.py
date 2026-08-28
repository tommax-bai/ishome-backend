#!/usr/bin/env -S uv run --quiet --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["pyyaml", "psycopg[binary]", "python-ulid"]
# ///
"""rulebook 种子灌库（规范 v2.2 规则 4.12：导入后 DB 即唯一真相，种子封存为审计记录）。

先跑 verify_seeds（硬违规即拒灌）；核验通过且 source 可定位无 pending 的知识条目置
calibration=calibrated（规则 4.10a：calibrated 只能由机检核验取得——本脚本即该跑批的授予点）。
check 形态无 calibration（规则 4.10b）。

用法：
  ./import_seeds.py --validate   # 单事务内建表+灌库+统计后 ROLLBACK（不留状态，Flyway 未跑也可用）
  ./import_seeds.py              # 真灌（要求 Flyway V2 已应用）；幂等：按 (asset_id,version) upsert
连接：ISHOME_DB_* 环境变量，默认本地 ishome-dev（localhost:15432/ishome）。
"""
from __future__ import annotations
import os, sys, glob, subprocess, yaml, psycopg
from ulid import ULID

HERE = os.path.dirname(os.path.abspath(__file__))
SEEDS = os.path.join(HERE, "..", "..", "services/project-svc/src/main/resources/rulebook-seeds")
MIGRATION_GLOB = os.path.join(HERE, "..", "..",
    "services/project-svc/src/main/resources/db/migration/V*__*rulebook*.sql")
SCHEMA = "svc_rulebook"
VALIDATE_SCHEMA = "svc_rulebook_validate"
"""--validate 走一次性影子 schema：正式 schema 已被 Flyway 建好后（V2 之后的常态），
再往里 CREATE TABLE 必然撞表。影子 schema 内建表→灌库→统计→ROLLBACK，DDL 在 PG 里同属事务，
连 schema 本身都不会留下。"""


def migration_sql(schema: str) -> str:
    """按版本号顺序拼接 rulebook 相关迁移（V2 建表、V3+ 增量），占位符替换为目标 schema。"""
    files = sorted(glob.glob(MIGRATION_GLOB),
                   key=lambda p: int(os.path.basename(p).split("__")[0][1:]))
    return f"CREATE SCHEMA IF NOT EXISTS {schema};\n" + "\n".join(
        open(f, encoding="utf-8").read().replace("${rulebook_schema}", schema)
        for f in files)

def dsn():
    return (f"host={os.environ.get('ISHOME_DB_HOST','localhost')} "
            f"port={os.environ.get('ISHOME_DB_PORT','15432')} "
            f"dbname={os.environ.get('ISHOME_DB_NAME','ishome')} "
            f"user={os.environ.get('ISHOME_DB_USER','ishome')} "
            f"password={os.environ.get('ISHOME_DB_PASSWORD','ishome-local-dev')}")

def run_verify() -> set[str]:
    r = subprocess.run([sys.executable and os.path.join(HERE, "verify_seeds.py")],
                       capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout); print("== 核验未过，拒绝灌库"); sys.exit(1)
    return {l.strip().split()[-1] for l in r.stdout.splitlines() if l.startswith("  ok ")}

def rows():
    import json
    def J(x): return json.dumps(x, ensure_ascii=False, default=str)  # YAML date → str
    for f in sorted(glob.glob(os.path.join(SEEDS, "*", "*.yaml"))):
        d = yaml.safe_load(open(f, encoding="utf-8")) or {}
        rel = os.path.relpath(f, SEEDS)
        domain = d.get("domain") or ("cross" if rel.startswith("_common") else rel.split("/")[0])
        defaults = {k[8:]: v for k, v in d.items() if k.startswith("default_")}
        if "knowledge_asset" in d:  # persona
            ka = d["knowledge_asset"]
            yield ("personas", rel, ka["id"], dict(
                asset_id=ka["id"], domain=ka["domain"], identity=d["identity"],
                judgment_samples=J(d.get("judgment_style", [])),
                assertion_budget=J(d.get("assertion_budget", [])),
                banned_terms=J(d.get("banned_terms", {})),
                calibration=ka.get("calibration", "draft"), source=ka.get("source"),
                consumers=J(ka.get("consumers", []))))
            continue
        form = d.get("form")
        if form == "vocabulary":  # _common/banned-terms
            for kind, terms in ((k, v) for k, v in d.items() if k not in ("scope","form")):
                aid = f"vocab-banned-{kind}"
                yield ("vocabularies", rel, aid, dict(
                    asset_id=aid, domain="cross", kind="banned_term",
                    terms=J({kind: terms}), calibration="draft", source="编译自 persona 公共禁词",
                    consumers=J(["machine-check"])))
            continue
        for it in d.get("items", []):
            m = {**defaults, **it}; aid = m["id"]; ctx = f"{rel}#{aid}"
            if form == "rule":
                yield ("rules", ctx, aid, dict(
                    asset_id=aid, domain=domain, layer=m["layer"], trigger=J(m["trigger"]),
                    content=m["content"], rationale=m.get("rationale"), severity=m["severity"],
                    point_spec=J(m["point_spec"]) if m.get("point_spec") else None,
                    calibration="draft", source=m.get("source"), source_pending=m.get("source_pending"),
                    conflict=bool(m.get("conflict", False)),
                    consumers=J(m.get("consumers", []))))
            elif form == "parameter":
                unit = m.get("unit") or (m.get("value") or {}).get("unit") if isinstance(m.get("value"), dict) else m.get("unit")
                yield ("parameters", ctx, aid, dict(
                    asset_id=aid, domain=domain, name=m["name"], number_class=m["number_class"],
                    value=J(m.get("value")) if m.get("value") is not None else None,
                    formula=m.get("formula"), unit=str(unit) if unit else None,
                    linked=J(m.get("linked")) if m.get("linked") else None,
                    calibration="draft", source=m.get("source"), source_pending=m.get("source_pending"),
                    conflict=bool(m.get("conflict", False)),
                    acquired=m.get("acquired"), consumers=J(m.get("consumers", []))))
            elif form == "attribute":
                props = m.get("props", {})
                yield ("attributes", ctx, aid, dict(
                    asset_id=aid, domain=domain, entity_type=d.get("entity_type", m.get("entity_type")),
                    name=m["name"], props=J(props), confidence=props.get("confidence"),
                    effective_from=props.get("effective_from"), effective_to=props.get("effective_to"),
                    calibration="draft", source=m.get("source"), source_2=m.get("source_2"),
                    source_pending=m.get("source_pending"), note=m.get("note"),
                    conflict=bool(m.get("conflict", False)),
                    consumers=J(m.get("consumers", []))))
            elif form == "check":
                refs = [m["max_from"]] if m.get("max_from") else []
                yield ("checks", ctx, aid, dict(
                    asset_id=aid, domain=domain, check_type=m["type"], scope=J(m.get("scope", [])),
                    pattern=m.get("pattern"), requirement=m.get("require"), message=m["message"],
                    decided_by=m["decided_by"], threshold_refs=J(refs)))

def main():
    validate = "--validate" in sys.argv
    eligible = run_verify()
    counts, flipped = {}, 0
    schema = VALIDATE_SCHEMA if validate else SCHEMA
    with psycopg.connect(dsn()) as conn, conn.cursor() as cur:
        if validate:
            cur.execute(migration_sql(schema))
        for table, ctx, aid, row in rows():
            if "calibration" in row and ctx in eligible:
                row["calibration"] = "calibrated"; flipped += 1
            cols = ["id"] + list(row)
            vals = [str(ULID())] + list(row.values())
            upd = ", ".join(f"{c}=EXCLUDED.{c}" for c in row if c != "asset_id") + ", updated_at=now()" \
                  if table != "checks" else ", ".join(f"{c}=EXCLUDED.{c}" for c in row if c != "asset_id") + ", updated_at=now()"
            cur.execute(
                f"INSERT INTO {schema}.{table} ({', '.join(cols)}) "
                f"VALUES ({', '.join(['%s']*len(vals))}) "
                f"ON CONFLICT (asset_id, version) DO UPDATE SET {upd}", vals)
            counts[table] = counts.get(table, 0) + 1
        for t in sorted(counts): print(f"  {t}: {counts[t]}")
        print(f"  calibrated（核验授予）: {flipped}")
        if validate:
            conn.rollback(); print("== --validate：已回滚，未留任何状态")
        else:
            conn.commit(); print("== 已提交。DB 即唯一真相，本目录种子封存为审计记录（规则 4.12）")

if __name__ == "__main__":
    main()
