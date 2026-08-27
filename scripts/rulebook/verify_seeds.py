#!/usr/bin/env -S uv run --quiet --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["pyyaml"]
# ///
"""核验跑批（规范 v2.2 规则 4.10a 四项 + 规则 4.10b 结构检查）——对 rulebook-seeds 全量运行。

硬违规（exit 1）：YAML 解析失败 / 区间 min>max / effective 倒挂 / 单位不在白名单 /
check 缺 decided_by / max_from 悬空 / consumers 悬空。
信息输出：可转 calibrated 的条目清单（source 可定位且无 source_pending）——转档动作由灌库侧执行，
本脚本只判定资格（规则 4.10a：calibrated 只能由机检核验取得）。
"""
from __future__ import annotations
import re, sys, glob, os
import yaml

SEEDS = os.path.join(os.path.dirname(__file__), "..", "..",
                     "services/project-svc/src/main/resources/rulebook-seeds")
ARTS = {f"art-{n}" for n in (
    "floorplan-current floorplan-dimensions daylight-analysis flow-analysis wall-structure "
    "plan-compare plan-final ergonomics-chapter ceiling-lighting-plan lighting-chapter "
    "material-mood space-render walkthrough-video hydro-layout material-chapter storage-chapter "
    "color-soft-chapter budget-chapter hydro-checklist quotation-checklist acceptance-checklist "
    "purchase-checklist").split()}
UNITS = {"mm","m","K","Ra","°","lx","㎡","投影㎡","延米","延米/㎡","点位","倍","种","比率","×环境照度","元","±比例"}
# 可定位 = 外部形态（标准号/URL/域名/信息价）。内部引用（"内部规范 §x.x"）不算——
# 经验条目借内部条文号伪装可核，正是规则 4.10b 要堵的路（首跑即误判过一批，故收紧）。
LOCATOR = re.compile(r"GB[/T ]?\s?\d|JGJ\s?\d|https?://|\.com|\.cn|信息价")

errors, warns, eligible = [], [], []

def load(path):
    with open(path, encoding="utf-8") as f:
        return yaml.safe_load(f)

def check_range(v, ctx):
    if isinstance(v, dict):
        if "min" in v and "max" in v and isinstance(v["min"],(int,float)) and isinstance(v["max"],(int,float)):
            if v["min"] > v["max"]: errors.append(f"{ctx}: min>{'max'} ({v['min']}>{v['max']})")
        for x in v.values(): check_range(x, ctx)
    elif isinstance(v, list) and len(v)==2 and all(isinstance(x,(int,float)) for x in v):
        if v[0] > v[1]: errors.append(f"{ctx}: 区间倒挂 {v}")
    elif isinstance(v, list):
        for x in v: check_range(x, ctx)

param_ids, check_ids = set(), set()
files = sorted(glob.glob(os.path.join(SEEDS, "*", "*.yaml")))
docs = {}
for f in files:
    try: docs[f] = load(f)
    except Exception as e: errors.append(f"{f}: YAML 解析失败 {e}"); continue

# 收集 id
for f, d in docs.items():
    for it in (d or {}).get("items", []):
        aid = it.get("id","")
        if aid.startswith("lkp-"): param_ids.add(aid)
        if aid.startswith("cr-"): check_ids.add(aid)

for f, d in docs.items():
    rel = os.path.relpath(f, SEEDS); d = d or {}
    form = d.get("form") or (d.get("knowledge_asset") or {}).get("form")
    defaults = {k[8:]: v for k, v in d.items() if k.startswith("default_")}
    items = d.get("items", [])
    if form == "persona" or "knowledge_asset" in d:
        ka = d.get("knowledge_asset", {})
        if not all(k in d for k in ("identity","judgment_style","assertion_budget","banned_terms")):
            errors.append(f"{rel}: persona 四件不齐（规则 4.13）")
        for a in d.get("assertion_budget", []):
            for r in a.get("requires", []):
                if r.startswith("lkp-") and r not in param_ids:
                    errors.append(f"{rel}: assertion_budget 引用悬空 {r}")
        continue
    for it in items:
        aid = it.get("id", "?"); ctx = f"{rel}#{aid}"
        merged = {**defaults, **it}
        if form == "check":
            if not merged.get("decided_by"): errors.append(f"{ctx}: check 缺 decided_by（规则 4.10b）")
            mf = merged.get("max_from")
            if mf and mf not in param_ids: errors.append(f"{ctx}: max_from 悬空 {mf}")
            continue  # check 不进 calibration 状态机
        # 知识条目：4.10a 四项
        check_range(merged.get("value"), ctx)
        ef, et = (merged.get("props") or {}).get("effective_from"), (merged.get("props") or {}).get("effective_to")
        if ef and et and str(ef) > str(et): errors.append(f"{ctx}: effective 倒挂")
        u = merged.get("unit") or (merged.get("value") or {}).get("unit") if isinstance(merged.get("value"), dict) else merged.get("unit")
        if u and str(u) not in UNITS: errors.append(f"{ctx}: 单位不在白名单 [{u}]")
        for c in merged.get("consumers", []):
            ok = c in ARTS or c in check_ids or c.startswith("gen-") or c == "machine-check"
            if not ok: errors.append(f"{ctx}: consumers 悬空 [{c}]")
        cal = merged.get("calibration", "draft")
        src, pend = merged.get("source") or "", merged.get("source_pending")
        if cal == "calibrated": errors.append(f"{ctx}: 种子不得预置 calibrated（只能由本跑批授予）")
        if src and LOCATOR.search(src) and not pend: eligible.append(ctx)
        elif not src: warns.append(f"{ctx}: source 为空")

print(f"== 核验跑批：{len(files)} 文件，参数 {len(param_ids)}，机检 {len(check_ids)}")
for e in errors: print("ERROR", e)
for w in warns: print("warn ", w)
print(f"== 可转 calibrated（source 可定位且无 pending）：{len(eligible)}")
for c in eligible: print("  ok ", c)
sys.exit(1 if errors else 0)
