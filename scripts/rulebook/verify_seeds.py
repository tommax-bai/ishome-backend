#!/usr/bin/env -S uv run --quiet --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["pyyaml"]
# ///
"""核验跑批（规范 v2.2 规则 4.10a 四项 + 规则 4.10b 结构检查）——对 rulebook-seeds 全量运行。

硬违规（exit 1）：YAML 解析失败 / 区间 min>max / effective 倒挂 / 单位不在白名单 /
check 缺 decided_by / max_from 悬空 / consumers 悬空 / 户型特征标记越界 /
value_kind 越界或与 value 形态不符 / 项名非法或不在受控词表 / 元信息键混进 value（规则 1.9）。
信息输出：可转 calibrated 的条目清单（source 可定位且无 source_pending）——转档动作由灌库侧执行，
本脚本只判定资格（规则 4.10a：calibrated 只能由机检核验取得）。
"""
from __future__ import annotations
import json, re, sys, glob, os
import yaml

HERE = os.path.dirname(os.path.abspath(__file__))
# 种子目录可经 ISHOME_SEEDS_PATH 覆盖（同 ISHOME_CONTRACTS_PATH 的既有约定）：
# test_verify_seeds.py 靠它把每条校验喂进真脚本跑黑盒正反例——校验自己不过测试，
# 与"规范不进 CI 等于不存在"是同一条纪律的下一级。
SEEDS = os.environ.get("ISHOME_SEEDS_PATH") or os.path.join(
    HERE, "..", "..", "services/project-svc/src/main/resources/rulebook-seeds")
ARTS = {f"art-{n}" for n in (
    "floorplan-current floorplan-dimensions daylight-analysis flow-analysis wall-structure "
    "plan-compare plan-final ergonomics-chapter ceiling-lighting-plan lighting-chapter "
    "material-mood space-render walkthrough-video hydro-layout material-chapter storage-chapter "
    "color-soft-chapter budget-chapter hydro-checklist quotation-checklist acceptance-checklist "
    "purchase-checklist").split()}
UNITS = {"mm","m","米","米/㎡","K","Ra","°","lx","㎡","投影㎡","延米","延米/㎡","点位","倍","种","%","元","±比例"}
# 「比率」已下架、「%」加入（用户裁决 2026-08-31，四域六条比率落点同批改源）：**「比率」是量的
# 名字不是记数词**，它进了 unit 就会被写手照抄进正文，拼出「0.9 比率」这种业主读不出的东西。
# 同理这批里另五条原本走 `dimensionless: true` 豁免——**无量纲不等于没有记数形态**：同为无量纲
# 比值的 lkp-accent-ratio 写着「倍」，印出来是「3–5 倍」，读得懂。豁免那条裁决（2026-08-29 晚
# 「真无量纲显式豁免」）一个字没动，改的是这六条的分类。判据与经过见中控仓
# 《交接文档-比率怎么印给业主.md》追记一。「±比例」暂留：那条讲的是我们内部怎么定区间宽度，
# **本就不该给业主看**，属"该不该引"不属"怎么印"，另立。
# 「×环境照度」已下架（2026-08-30 晚改源为「倍」）：单位自本轮起由写手写进正文，而它含灯光域
# 禁词「照度」——同一个词既是计量单位又禁止对业主说，机器会一边要求写一边因为写了而打回。
# 「延米」保留在白名单里（它是真单位，将来采购/报价页要用），但收纳域的两条已改源为「米」，
# 因为「延米」是该域禁词。撞不撞由下面 unit_collides_banned_term 按域判，不靠这张表判。
# 可定位 = 外部形态（标准号/URL/域名/信息价）。内部引用（"内部规范 §x.x"）不算——
# 经验条目借内部条文号伪装可核，正是规则 4.10b 要堵的路（首跑即误判过一批，故收紧）。
LOCATOR = re.compile(r"GB[/T ]?\s?\d|JGJ\s?\d|https?://|\.com|\.cn|信息价")
# check 入册状态（规则 4.17 门禁二；V4 迁移的 ck_checks_status 同集合）
CHECK_STATUS = {"observing", "active", "retired"}
# —— 两层模型（规则 1.9，规范 v2.8）：一条落点＝若干项，一项的值＝一个数或一个区间 ——
# value_kind 七值闭集：可引用性与渲染形态都由它判定，不靠推断键名。
VALUE_KINDS = {"single", "range", "scenario", "tier", "dimension", "component", "comparison"}
# 档位闭集**有序**：comparison 的 {高档}-vs-{低档} 形态靠这个序判高低，low<medium<high
TIER_ITEMS = ("low", "medium", "high")
DIMENSION_ITEMS = {"depth", "width", "height"}
ITEM_NAME = re.compile(r"^[a-z][a-z0-9-]*$")
COMPARISON_NAME = re.compile(r"^([a-z]+)-vs-([a-z]+)$")
# 元信息键：与项同层就意味着 {lkp-x.unit}（引用出一个单位字符串）是语法上合法的写法，
# 靠"别那么写"约束不住——故一律不进 value，各归各的字段（规则 1.9 二）。
VALUE_META_KEYS = {"unit", "plane", "reference_plane"}
# 自造精度声明：一律禁止（规则 4.10e，用户裁决 2026-08-30）。真样本＝lkp-storage-closed-ratio
# 的 tolerance: 0.1——自种子首版就在，而 source 只给了 0.8（"二八原则"），±0.1 没有源。
# 它禁的不是"表达不确定"，是**用一个自己编的数字表达它**：不确定性由标注承担（规则 4.10c）。
PRECISION_CLAIM_KEYS = {"tolerance", "approx", "margin", "error"}
# 户型特征标记闭集（规则 6.3 触发字段）的唯一真源在 contracts，本脚本**读它不复制它**：
# 复制一份就是"注册表与规则数据两套写法"，改一侧不改另一侧即静默失效（同锁定文案注册表纪律三）。
# 检出路径同 shared/contracts 模块的约定：默认同级检出 ../ishome-contracts，CI 用 contracts-checkout；
# 均可经 ISHOME_CONTRACTS_PATH 覆盖。
LAYOUT_FEATURES_REL = "rulebook/layout_features.json"
# 落点项名的受控词表（开集两类 scenario/component，规则 1.9 三）同理：唯一真源在 contracts
ANCHOR_ITEMS_REL = "registries/anchor_items.json"
CONTRACTS_CANDIDATES = [
    os.environ.get("ISHOME_CONTRACTS_PATH"),
    os.path.join(HERE, "..", "..", "..", "ishome-contracts"),
    os.path.join(HERE, "..", "..", "contracts-checkout"),
]


def contract_json(rel: str):
    """读 contracts 检出里的一份机器可读注册表。找不到即**响亮失败**——静默跳过一道校验等于
    它不存在，而这两道拦的都是同一类最贵的失效形态：名字写错、既不生效也不报错。"""
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


# 户型特征标记闭集（规则 6.3）与落点项名受控词表（规则 1.9 三）：本脚本**读契约不复制契约**
LAYOUT_FEATURES = set(contract_json(LAYOUT_FEATURES_REL).get("features", {}))
ANCHOR_ITEMS = {kind: set(names)
                for kind, names in contract_json(ANCHOR_ITEMS_REL).get("items", {}).items()}

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

def is_number(v):
    """YAML 里 True/False 也是 int 的子类——布尔不是数值，先挡掉。"""
    return isinstance(v, (int, float)) and not isinstance(v, bool)


def check_range_object(obj, ctx, where):
    """一项的值是区间时的形态：只有 min/max 两键，至少给一侧，两侧都是数。

    多出来的键一律报错——**min/max 是项的值形态，不是项**（规则 1.9 一）。放任第三个键进来，
    "{lkp-x.min}"这种写法就又有了土壤，而它正是两层模型要用结构堵死的那个老问题。
    """
    if not isinstance(obj, dict):
        errors.append(f"{ctx}: {where} 须为 {{min,max}} 区间对象，现为 {type(obj).__name__}")
        return
    extra = [k for k in obj if k not in ("min", "max") and k not in VALUE_META_KEYS]
    if extra:
        errors.append(f"{ctx}: {where} 区间只有 min/max 两键，多出 {extra}"
                      f"（min/max 是项的值形态不是项，规则 1.9 一）")
    for k in ("min", "max"):
        if k in obj and not is_number(obj[k]):
            errors.append(f"{ctx}: {where}.{k} 必须是数")
    if not any(k in obj for k in ("min", "max")):
        errors.append(f"{ctx}: {where} 至少要有 min 或 max 一侧（单边界区间只给一侧）")


def check_value_shape(merged, ctx):
    """两层模型的形态与项名校验（规则 1.9，规范 v2.8）。

    三件事：①value_kind 在七值闭集内且与 value 的**实际形态**一致；②项名合法且落在其
    value_kind 的闭集/受控词表内（不在即拒灌——规则 1.9 三最后一条原文点名的执行位）；
    ③元信息不得与项同层。规则写了没人执行，与既有的"取值不校验"是同一个坑。
    """
    claimed = sorted(k for k in PRECISION_CLAIM_KEYS if k in merged)
    if claimed:
        errors.append(
            f"{ctx}: 带自造精度声明 {claimed}（规则 4.10e）——不确定性由标注承担"
            f"（规则 4.10c：经验条目照发、随页挂标注、语域限建议口吻），"
            f"不由一个没有源的数字重复表达。真有区间就用 value_kind=range 给 min/max，"
            f"但那两个数必须有源，不能折算出来"
        )
    kind, value = merged.get("value_kind"), merged.get("value")
    if kind is None:
        if value is not None:
            errors.append(f"{ctx}: 有 value 却无 value_kind"
                          f"（可引用性与渲染形态由 value_kind 判定，不靠推断，规则 1.9 一）")
        elif merged.get("formula"):
            # 公式落点此刻不产出落点（可执行形态未登记即走 gap-），登记之后契约要求 valueKind 必填。
            # 报 warn 不报 error：硬拦会逼出一个瞎填的 value_kind，那比缺席更坏（坦白缺口，规则 4.18）。
            warns.append(f"{ctx}: 公式落点未声明 value_kind——可执行形态一旦登记即产出落点，"
                         f"届时契约 anchors[].valueKind 必填")
        return
    if kind not in VALUE_KINDS:
        errors.append(f"{ctx}: value_kind 非法 [{kind}]，闭集 {sorted(VALUE_KINDS)}（规则 1.9 一）")
        return
    if value is None:
        return          # 公式落点：形态一致性等求出值的那天再判，此处只认闭集
    if kind == "single":
        if not is_number(value):
            errors.append(f"{ctx}: value_kind=single 的 value 须为标量"
                          f"（一个匿名项，值是数），现为 {type(value).__name__}")
        return
    if not isinstance(value, dict):
        errors.append(f"{ctx}: value_kind={kind} 的 value 须为映射，现为 {type(value).__name__}")
        return
    for key in value:
        if key in VALUE_META_KEYS:
            errors.append(f"{ctx}: 元信息键 [{key}] 不得进 value——单位归 unit、参考平面归 "
                          f"reference_plane（规则 1.9 二）")
    if kind == "range":
        check_range_object(value, ctx, "value")
        return
    # 其余五类：项名 → 标量，或 项名 → {min,max}
    names = [n for n in value if n not in VALUE_META_KEYS]
    for name in names:
        if not ITEM_NAME.match(str(name)):
            errors.append(f"{ctx}: 项名 [{name}] 非法——ASCII 小写 kebab-case "
                          f"{ITEM_NAME.pattern}（与落点标识同一套，规则 1.9 三）")
            continue
        item = value[name]
        if isinstance(item, dict):
            check_range_object(item, ctx, f"value.{name}")
        elif not is_number(item):
            errors.append(f"{ctx}: 项 [{name}] 的值须为数或区间"
                          f"（一项的值只有这两种，规则 1.9 一），现为 {type(item).__name__}")
    if kind == "tier":
        bad = [n for n in names if n not in TIER_ITEMS]
        if bad:
            errors.append(f"{ctx}: tier 项名越界 {bad}，闭集 {list(TIER_ITEMS)}"
                          f"（闭集新增取值即改规范，规则 1.9 三）")
    elif kind == "dimension":
        bad = [n for n in names if n not in DIMENSION_ITEMS]
        if bad:
            errors.append(f"{ctx}: dimension 项名越界 {bad}，闭集 {sorted(DIMENSION_ITEMS)}"
                          f"（闭集新增取值即改规范，规则 1.9 三）")
    elif kind == "comparison":
        for n in names:
            m = COMPARISON_NAME.match(str(n))
            if not m or m.group(1) not in TIER_ITEMS or m.group(2) not in TIER_ITEMS:
                errors.append(f"{ctx}: comparison 项名 [{n}] 形态非法——须为 {{高档}}-vs-{{低档}}，"
                              f"两侧档名取自 tier 闭集 {list(TIER_ITEMS)}（规则 1.9 三）")
            elif TIER_ITEMS.index(m.group(1)) <= TIER_ITEMS.index(m.group(2)):
                errors.append(f"{ctx}: comparison 项名 [{n}] 高低颠倒——形态是 {{高档}}-vs-{{低档}}"
                              f"（如 high-vs-medium），左高右低不可换序")
    else:   # scenario / component：受控词表（开集），不在词表内即拒灌
        vocab = ANCHOR_ITEMS.get(kind, set())
        bad = [n for n in names if n not in vocab]
        if bad:
            errors.append(f"{ctx}: {kind} 项名不在受控词表 {bad}——先把「项名 + 中文语义 + 首次出处」"
                          f"登记进 contracts {ANCHOR_ITEMS_REL} 再改源（规则 1.9 三：译名登记一次，"
                          f"不由每次改源临场发挥）")


def banned_terms_of(domain_dir: str, docs: dict) -> set[str]:
    """该域禁词 = 跨域表 + persona 域内禁词（与 reportgen collect_banned_terms 同口径）。

    两处口径订正（2026-08-30）：
    - 跨域表按 `weak`/`methodology`/… 逐类列在**顶层**，没有 `items` 键——原先读 `items`
      恒取到空，**那 21 个公共禁词从没进过核验集**；
    - persona 侧原先只读 `domain_extra` 一个键，而成文线读的是 banned_terms 下**全部列表值**
      （键即组名）。灯光域把键改成 `jargon` 之后，只读一个键就会漏掉它那四个词。
    两处都会让"单位撞禁词"那道守卫漏拦——它按本域禁词集判。
    """
    terms: set[str] = set()
    def walk(x):
        if isinstance(x, str): terms.add(x)
        elif isinstance(x, list):
            for i in x: walk(i)
        elif isinstance(x, dict):
            for v in x.values(): walk(v)
    common = docs.get(os.path.join(SEEDS, "_common", "banned-terms.yaml")) or {}
    for key, value in common.items():
        if key not in ("scope", "form"):
            walk(value)
    persona = docs.get(os.path.join(SEEDS, domain_dir, "persona.yaml")) or {}
    for it in persona.get("items", [persona]):
        bt = (it or {}).get("banned_terms") or {}
        for key, value in bt.items():
            if key != "inherit":  # inherit 是文件路径不是词面
                walk(value)
    return {t for t in terms if isinstance(t, str) and t.strip()}


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
        # 两层模型（规则 1.9）：只管 parameter——attribute 的 props 是实体属性包不是落点值形态，
        # 单价资产的落点由投影产出（形态恒为区间，权威在 RulebookEvaluator，见 §投影）。
        if form == "parameter":
            check_value_shape(merged, ctx)
        ef, et = (merged.get("props") or {}).get("effective_from"), (merged.get("props") or {}).get("effective_to")
        if ef and et and str(ef) > str(et): errors.append(f"{ctx}: effective 倒挂")
        # 单位只认资产自己的 unit 字段：v2.8 起 value 里不再有 unit（规则 1.9 二），
        # 留着那条回退等于给"元信息混进 value"留一条仍然能通过核验的路
        u = merged.get("unit")
        if u and str(u) not in UNITS: errors.append(f"{ctx}: 单位不在白名单 [{u}]")
        # 单位撞本域禁词即拒灌（2026-08-30 晚）：单位自本轮起**由写手写进正文**（我们预制、它照抄），
        # 而禁词是不许对业主说的词——同一个词两种身份，机器会一边要求它写、一边因为它写了而打回，
        # 这一章永远过不了检。拦在这里是最早的一道；reportgen 侧同名守卫留着，是为了那些**已经
        # 发出去的老 release**（快照不可变，改源只能影响下一次发版）。两处同判据不同射程，非重复。
        if u:
            dom_dir = os.path.basename(os.path.dirname(f))
            for term in banned_terms_of(dom_dir, docs):
                if term in str(u):
                    errors.append(f"{ctx}: 单位 [{u}] 含本域禁词「{term}」——单位要由写手写进正文，"
                                  f"禁词写了必被打回；改源换一个能写给业主看的单位")
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
