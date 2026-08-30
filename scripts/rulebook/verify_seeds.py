#!/usr/bin/env -S uv run --quiet --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["pyyaml"]
# ///
"""核验跑批（规范 v2.2 规则 4.10a 四项 + 规则 4.10b 结构检查）——对 rulebook-seeds 全量运行。

硬违规（exit 1）：YAML 解析失败 / 区间 min>max / effective 倒挂 / 单位不在白名单 /
check 缺 decided_by / max_from 悬空 / consumers 悬空 / 户型特征标记越界。
信息输出：可转 calibrated 的条目清单（source 可定位且无 source_pending）——转档动作由灌库侧执行，
本脚本只判定资格（规则 4.10a：calibrated 只能由机检核验取得）。
"""
from __future__ import annotations
import json, re, sys, glob, os
import yaml

HERE = os.path.dirname(os.path.abspath(__file__))
SEEDS = os.path.join(HERE, "..", "..",
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
# check 入册状态（规则 4.17 门禁二；V4 迁移的 ck_checks_status 同集合）
CHECK_STATUS = {"observing", "active", "retired"}
# 户型特征标记闭集（规则 6.3 触发字段）的唯一真源在 contracts，本脚本**读它不复制它**：
# 复制一份就是"注册表与规则数据两套写法"，改一侧不改另一侧即静默失效（同锁定文案注册表纪律三）。
# 检出路径同 shared/contracts 模块的约定：默认同级检出 ../ishome-contracts，CI 用 contracts-checkout；
# 均可经 ISHOME_CONTRACTS_PATH 覆盖。
LAYOUT_FEATURES_REL = "rulebook/layout_features.json"
CONTRACTS_CANDIDATES = [
    os.environ.get("ISHOME_CONTRACTS_PATH"),
    os.path.join(HERE, "..", "..", "..", "ishome-contracts"),
    os.path.join(HERE, "..", "..", "contracts-checkout"),
]


def layout_features() -> set[str]:
    """户型特征标记闭集。找不到契约检出即**响亮失败**——静默跳过这道校验等于它不存在，
    而它拦的正是本项目最贵的失效形态：标记名写错 → 规则永远不触发且不报错（契约 §四）。"""
    for base in CONTRACTS_CANDIDATES:
        if not base:
            continue
        path = os.path.join(base, LAYOUT_FEATURES_REL)
        if os.path.isfile(path):
            with open(path, encoding="utf-8") as f:
                return set(json.load(f).get("features", {}))
    print(f"== 找不到 {LAYOUT_FEATURES_REL}：clone ishome-contracts 到 backend 同级目录，"
          f"或设 ISHOME_CONTRACTS_PATH=<检出路径>（试过：{[c for c in CONTRACTS_CANDIDATES if c]}）")
    sys.exit(1)


LAYOUT_FEATURES = layout_features()

errors, warns, eligible, conflicts, judges = [], [], [], [], []

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

# 收集 id。落点（lkp-）有**两个来源**，与求值线一致：
#   ① parameters 表的 lkp- 资产；
#   ② attributes 里 entity_type=work_item 的单价资产**投影**（attr-price-x → lkp-price-x，
#      规则 5.15 造价章；投影规则的权威实现在 RulebookEvaluator#anchorIdOf，此处按同一条规则镜像）。
# 不镜像就会把 persona 里指向单价落点的 requires 全判成悬空——而它们在运行时是真实存在的落点。
# 镜像即两处各写一遍同一条规则（Java 求值线 / Python 核验），改投影规则时两处都要动：
# 两条线本就一个跑运行时一个跑编译期，没有共享代码的位置，宁可显式重复也不发明一层配置。
for f, d in docs.items():
    d = d or {}
    entity_type_of_doc = d.get("entity_type")
    for it in d.get("items", []):
        aid = it.get("id","")
        if aid.startswith("lkp-"): param_ids.add(aid)
        if aid.startswith("cr-"): check_ids.add(aid)
        # 文档级 entity_type 优先，与 import_seeds 的取法逐字一致
        if (entity_type_of_doc or it.get("entity_type")) == "work_item" and aid.startswith("attr-"):
            param_ids.add("lkp-" + aid[len("attr-"):])

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
            st = merged.get("status", "active")
            if st not in CHECK_STATUS: errors.append(f"{ctx}: status 非法 [{st}]，取值 {sorted(CHECK_STATUS)}")
            exs = merged.get("examples") or []
            if not isinstance(exs, list): errors.append(f"{ctx}: examples 须为列表")
            for k, ex in enumerate(exs if isinstance(exs, list) else []):
                if not isinstance(ex, dict) or not all(
                        isinstance(ex.get(f), str) and ex.get(f).strip() for f in ("bad", "why", "fixed")):
                    errors.append(f"{ctx}: examples[{k}] 三件不齐（bad/why/fixed 均须非空文本）")
            if merged.get("type") == "semantic_judge" and not exs:
                errors.append(f"{ctx}: semantic_judge 判据无 examples——判官无据可依（规则 4.17）")
            # 与"种子不得预置 calibrated"同构：拦截权只能由观察期数据授予，不能在种子里自己写上。
            # 判官与写手同源，观察态（规则 4.17 入册门禁第二道）是唯一防漂移机制，绕不得。
            if exs and st != "observing":
                errors.append(f"{ctx}: 判官判据种子不得预置 status={st}（观察态是入册门禁第二道，规则 4.17）")
            if exs: judges.append(f"{ctx} status={st} examples={len(exs)}")
            continue  # check 不进 calibration 状态机
        if form == "rule":
            # 户型特征触发的标记名必须 ∈ 闭集（契约 rulebook/layout_features.md §四，两侧校验的核验侧）。
            # 越界或缺名都拦在入库前：求值线的匹配语义是"键存在即触发"，键名写错既不触发也不报错——
            # 它会一路发到 release、进每一份包，而症状只是"这条规则好像从来没出现过"。
            trigger = merged.get("trigger") or {}
            if trigger.get("type") == "layout_feature":
                feature = trigger.get("layout_feature")
                if not feature:
                    errors.append(f"{ctx}: layout_feature 触发缺标记名（trigger.layout_feature）")
                elif feature not in LAYOUT_FEATURES:
                    errors.append(f"{ctx}: 户型特征标记越界 [{feature}]，闭集见 contracts "
                                  f"{LAYOUT_FEATURES_REL}：{sorted(LAYOUT_FEATURES)}")
        # 知识条目：4.10a 四项
        check_range(merged.get("value"), ctx)
        ef, et = (merged.get("props") or {}).get("effective_from"), (merged.get("props") or {}).get("effective_to")
        if ef and et and str(ef) > str(et): errors.append(f"{ctx}: effective 倒挂")
        u = merged.get("unit") or (merged.get("value") or {}).get("unit") if isinstance(merged.get("value"), dict) else merged.get("unit")
        if u and str(u) not in UNITS: errors.append(f"{ctx}: 单位不在白名单 [{u}]")
        # 量纲必填（用户裁决 2026-08-29 晚，立案=lkp-tv-distance 正文渲出裸数无量纲）：
        # 带数值或公式的资产须有单位；真正无量纲的显式 dimensionless: true 豁免——
        # 豁免是声明"无单位是事实"，不是"忘了填"的同义词。
        def _has_number(v):
            if isinstance(v, bool): return False
            if isinstance(v, (int, float)): return True
            if isinstance(v, dict): return any(_has_number(x) for x in v.values())
            if isinstance(v, list): return any(_has_number(x) for x in v)
            return False
        if not u and not merged.get("dimensionless") and (merged.get("formula") or _has_number(merged.get("value"))):
            errors.append(f"{ctx}: 数值/公式资产缺 unit（量纲必填，裁决 2026-08-29 晚；真无量纲须显式 dimensionless: true）")
        for c in merged.get("consumers", []):
            ok = c in ARTS or c in check_ids or c.startswith("gen-") or c == "machine-check"
            if not ok: errors.append(f"{ctx}: consumers 悬空 [{c}]")
        cal = merged.get("calibration", "draft")
        src, pend = merged.get("source") or "", merged.get("source_pending")
        if cal == "calibrated": errors.append(f"{ctx}: 种子不得预置 calibrated（只能由本跑批授予）")
        # conflict 条目一律不够格（规则 4.16①）：源间互斥时"依据可定位"不构成可核性——
        # 两个都能定位、彼此打架的源，定位性不等于正确性。发布侧另有排除（publish_release）。
        if merged.get("conflict"): conflicts.append(ctx)
        elif src and LOCATOR.search(src) and not pend: eligible.append(ctx)
        elif not src: warns.append(f"{ctx}: source 为空")

print(f"== 核验跑批：{len(files)} 文件，参数 {len(param_ids)}，机检 {len(check_ids)}")
for e in errors: print("ERROR", e)
for w in warns: print("warn ", w)
print(f"== 可转 calibrated（source 可定位且无 pending）：{len(eligible)}")
for c in eligible: print("  ok ", c)
if conflicts:
    print(f"== conflict 条目（源间不一致，不进 release，规则 4.16①）：{len(conflicts)}")
    for c in conflicts: print("  conflict ", c)
if judges:
    print(f"== 判官反例判据（样例只收真跑样本，禁想象填充，规范 v2.3 §12）：{len(judges)}")
    for j in judges: print("  judge ", j)
sys.exit(1 if errors else 0)
