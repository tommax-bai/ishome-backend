#!/usr/bin/env -S uv run --quiet --script
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""核验跑批自己的回归（两层模型部分，规则 1.9 / 规范 v2.8）——正反各一逐条钉住。

黑盒跑真脚本：每个用例写一份只含一条落点的临时种子目录，用 ISHOME_SEEDS_PATH 指过去，
断言该条校验**该报的报了、不该报的没报**。为什么不 import 校验函数单测：verify_seeds.py 是
带 uv 头的单文件脚本，跑法就是被直接执行——测它被执行时的行为，才是测真正上线的那条路径。

用法：./test_verify_seeds.py   （退出码 0 = 全绿）
"""
from __future__ import annotations
import os, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
VERIFY = os.path.join(HERE, "verify_seeds.py")

failures: list[str] = []


def run(item_yaml: str) -> str:
    """把一条落点写成一份最小 parameters.yaml，跑真核验脚本，返回它的全部输出。"""
    with tempfile.TemporaryDirectory() as seeds:
        domain = os.path.join(seeds, "lighting")
        os.makedirs(domain)
        with open(os.path.join(domain, "parameters.yaml"), "w", encoding="utf-8") as f:
            f.write("domain: lighting\nform: parameter\nrelease: null\nitems:\n" + item_yaml)
        env = {**os.environ, "ISHOME_SEEDS_PATH": seeds}
        done = subprocess.run([VERIFY], capture_output=True, text=True, env=env)
        return done.stdout + done.stderr


def item(**fields) -> str:
    """最小落点：只填本用例关心的字段，其余给到能过掉无关校验的缺省。"""
    body = {"id": "lkp-probe", "name": "探针落点", "number_class": "analysis", "unit": "lx",
            **fields}
    lines = [f"  - id: {body.pop('id')}"]
    lines += [f"    {k}: {v}" for k, v in body.items()]
    return "\n".join(lines) + "\n"


def case(name: str, item_yaml: str, needle: str, *, expect_error: bool):
    """一条用例：needle 是那条校验的错误话术片段。"""
    out = run(item_yaml)
    hit = any(line.startswith("ERROR") and needle in line for line in out.splitlines())
    if hit != expect_error:
        failures.append(f"{name}: 期望{'报错' if expect_error else '不报错'}含「{needle}」，实际：\n{out}")
    print(f"  {'ok  ' if hit == expect_error else 'FAIL'} {name}")


# ① value_kind 必须在七值闭集内
case("value_kind 越界即拒", item(value_kind="interval", value="{min: 1, max: 2}"),
     "value_kind 非法", expect_error=True)
case("value_kind 在闭集内即放行", item(value_kind="range", value="{min: 1, max: 2}"),
     "value_kind 非法", expect_error=False)

# ② 有 value 必须声明 value_kind（形态不靠推断）
case("有 value 无 value_kind 即拒", item(value="{min: 1, max: 2}"),
     "有 value 却无 value_kind", expect_error=True)
case("声明了即放行", item(value_kind="range", value="{min: 1, max: 2}"),
     "有 value 却无 value_kind", expect_error=False)

# ③ value_kind 与 value 的实际形态一致：single 必须标量
case("single 带 v 壳即拒", item(value_kind="single", value="{v: 3000}"),
     "single 的 value 须为标量", expect_error=True)
case("single 给标量即放行", item(value_kind="single", value="3000"),
     "single 的 value 须为标量", expect_error=False)

# ④ range 必须是 min/max（min/max 是值形态不是项）
case("range 混进第三个键即拒", item(value_kind="range", value="{min: 1, max: 2, step: 1}"),
     "区间只有 min/max 两键", expect_error=True)
case("range 单边界即放行", item(value_kind="range", value="{min: 1}"),
     "区间只有 min/max 两键", expect_error=False)

# ⑤ 其余五类必须是项名映射
case("component 给标量即拒", item(value_kind="component", value="3", dimensionless="true"),
     "的 value 须为映射", expect_error=True)
case("component 给项名映射即放行",
     item(value_kind="component", value="{main: 0.6}", dimensionless="true"),
     "的 value 须为映射", expect_error=False)

# ⑥ 项名形态：ASCII 小写 kebab-case
case("项名带下划线/大写即拒",
     item(value_kind="component", value="{Main_Color: 0.6}", dimensionless="true"),
     "非法——ASCII 小写 kebab-case", expect_error=True)
case("kebab-case 项名即放行",
     item(value_kind="component", value="{main-material: 0.6}", dimensionless="true"),
     "非法——ASCII 小写 kebab-case", expect_error=False)

# ⑦ tier 闭集 low|medium|high
case("tier 项名越界即拒", item(value_kind="tier", value="{mid: 0.3}", dimensionless="true"),
     "tier 项名越界", expect_error=True)
case("tier 闭集内即放行", item(value_kind="tier", value="{medium: 0.3}", dimensionless="true"),
     "tier 项名越界", expect_error=False)

# ⑧ dimension 闭集 depth|width|height
case("dimension 用自造缩写即拒",
     item(value_kind="dimension", value="{min_d: 800}", unit="mm"),
     "非法——ASCII 小写 kebab-case", expect_error=True)
case("dimension 越界项名即拒",
     item(value_kind="dimension", value="{diameter: 800}", unit="mm"),
     "dimension 项名越界", expect_error=True)
case("dimension 闭集内即放行",
     item(value_kind="dimension", value="{depth: {min: 800}}", unit="mm"),
     "dimension 项名越界", expect_error=False)

# ⑨ comparison 形态 {高档}-vs-{低档}，两侧取自 tier 闭集
case("comparison 侧名不在 tier 闭集即拒",
     item(value_kind="comparison", value="{mid-vs-low: {min: 1.4, max: 2.0}}", unit="倍"),
     "形态非法", expect_error=True)
case("comparison 高低颠倒即拒",
     item(value_kind="comparison", value="{medium-vs-high: {min: 1.4, max: 2.0}}", unit="倍"),
     "高低颠倒", expect_error=True)
case("comparison 合规即放行",
     item(value_kind="comparison", value="{high-vs-medium: {min: 1.4, max: 2.0}}", unit="倍"),
     "形态非法", expect_error=False)

# ⑩ scenario / component 走受控词表（开集）：不在词表内即拒灌
case("scenario 生造项名即拒",
     item(value_kind="scenario", value="{cooking: 300}"),
     "项名不在受控词表", expect_error=True)
case("scenario 词表内即放行",
     item(value_kind="scenario", value="{general: 100, reading: 300}"),
     "项名不在受控词表", expect_error=False)
case("component 生造项名即拒",
     item(value_kind="component", value="{kitchen-island: 0.2}", dimensionless="true"),
     "项名不在受控词表", expect_error=True)
case("component 词表内即放行",
     item(value_kind="component", value="{main-material: 0.2}", dimensionless="true"),
     "项名不在受控词表", expect_error=False)

# ⑪ 元信息不得与项同层（规则 1.9 二）
case("unit 混进 value 即拒",
     item(value_kind="scenario", value="{general: 100, unit: lx}"),
     "元信息键 [unit] 不得进 value", expect_error=True)
case("plane 混进 value 即拒",
     item(value_kind="scenario", value='{general: 100, plane: "0.75m 水平面"}'),
     "元信息键 [plane] 不得进 value", expect_error=True)
case("元信息各归各字段即放行",
     item(value_kind="scenario", value="{general: 100}", reference_plane='"0.75m 水平面"'),
     "不得进 value", expect_error=False)

# ⑫ 自造精度声明一律禁止（规则 4.10e，用户裁决 2026-08-30）
case("点值带 tolerance 即拒",
     item(value_kind="single", value="0.8", tolerance="0.1", dimensionless="true"),
     "带自造精度声明", expect_error=True)
case("approx 同禁（换个词面不换性质）",
     item(value_kind="single", value="0.8", approx="0.1", dimensionless="true"),
     "带自造精度声明", expect_error=True)
case("有源的真区间照常放行——禁的是自造精度声明，不是禁表达区间",
     item(value_kind="range", value="{min: 0.7, max: 0.9}", dimensionless="true"),
     "带自造精度声明", expect_error=False)

# ⑬ 单位撞禁词的守卫按**本域全部禁词**判（口径订正 2026-08-30）
def run_with(files: dict[str, str]) -> str:
    """跑真核验，种子目录里额外写几份文件（公共禁词表 / persona）。"""
    with tempfile.TemporaryDirectory() as seeds:
        for rel, body in files.items():
            path = os.path.join(seeds, rel)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "w", encoding="utf-8") as f:
                f.write(body)
        env = {**os.environ, "ISHOME_SEEDS_PATH": seeds}
        done = subprocess.run([VERIFY], capture_output=True, text=True, env=env)
        return done.stdout + done.stderr


def unit_case(name: str, unit: str, *, common: str, persona_group: str, expect_error: bool):
    out = run_with({
        "_common/banned-terms.yaml": f"scope: cross-domain\nform: vocabulary\n{common}\n",
        "lighting/persona.yaml": (
            "domain: lighting\nform: persona\nrelease: null\n"
            "identity: 灯光顾问\njudgment_style: []\nassertion_budget: []\n"
            f"banned_terms:\n  inherit: _common/banned-terms.yaml\n  {persona_group}\n"),
        "lighting/parameters.yaml": (
            "domain: lighting\nform: parameter\nrelease: null\nitems:\n"
            f"  - id: lkp-probe\n    name: 探针落点\n    number_class: analysis\n"
            f"    unit: {unit}\n    value_kind: single\n    value: 3\n"),
    })
    hit = any(line.startswith("ERROR") and "含本域禁词" in line for line in out.splitlines())
    if hit != expect_error:
        failures.append(f"{name}: 期望{'报错' if expect_error else '不报错'}，实际：\n{out}")
    print(f"  {'ok  ' if hit == expect_error else 'FAIL'} {name}")


# 立案：公共表按 weak/methodology 逐类列在**顶层**，原先读 `items` 恒取到空——21 个公共禁词
# 从没进过核验集，撞了也不报。
unit_case("单位撞跨域公共禁词要拦", "依据/㎡",
          common='methodology: ["依据"]', persona_group='jargon: ["照度"]', expect_error=True)
# 立案：persona 侧原先只读 domain_extra 一个键；灯光域改名 jargon 后只读一个键就漏了那四个词。
unit_case("单位撞域内禁词要拦（组名不是 domain_extra 也要认）", "×环境照度",
          common='methodology: ["依据"]', persona_group='jargon: ["照度"]', expect_error=True)
unit_case("不撞就放行", "lx",
          common='methodology: ["依据"]', persona_group='jargon: ["照度"]', expect_error=False)


# ⑭ 数据面不许含本域禁词（用户裁决 2026-09-02）——整册首跑头号归因（release 数据自己写着
# 它禁的词）在发版源头拦。扫描面＝identity ∪ assertion_budget.predicate ∪ rule.content/rationale；
# judgment_style 的 bad 示范是结构化负例，**刻意不在面内**，这里正反都钉住。
def data_case(name: str, *, identity: str = "灯光顾问",
              assertion: str = "[]", judgment: str = "[]",
              rule_content: str | None = None, expect_error: bool):
    files = {
        "_common/banned-terms.yaml": 'scope: cross-domain\nform: vocabulary\nmethodology: ["依据"]\n',
        "lighting/persona.yaml": (
            "domain: lighting\nform: persona\nrelease: null\n"
            f"identity: {identity}\n"
            f"judgment_style: {judgment}\n"
            f"assertion_budget: {assertion}\n"
            'banned_terms:\n  inherit: _common/banned-terms.yaml\n  jargon: ["照度"]\n'),
    }
    if rule_content is not None:
        files["lighting/rules.yaml"] = (
            "domain: lighting\nform: rule\nrelease: null\nitems:\n"
            f'  - {{id: rule-probe, layer: tier-practice, content: "{rule_content}", '
            'rationale: "探针", severity: recommended, calibration: draft}\n')
    out = run_with(files)
    hit = any(line.startswith("ERROR") and "数据自己写着它禁的词" in line for line in out.splitlines())
    if hit != expect_error:
        failures.append(f"{name}: 期望{'报错' if expect_error else '不报错'}，实际：\n{out}")
    print(f"  {'ok  ' if hit == expect_error else 'FAIL'} {name}")


data_case("身份句含本域禁词即拒（material「优异」的形态）",
          identity="照度是关键", expect_error=True)
data_case("断言题目名含本域禁词即拒（lighting 题目名的形态）",
          assertion="[{predicate: 重点照明照度倍数, requires: []}]", expect_error=True)
data_case("规则正文含本域禁词即拒（ergonomics 宠物规则的形态）",
          rule_content="阅读位照度要够", expect_error=True)
data_case("judgment_style 的 bad 示范不在面内（结构化负例，机器认得出）",
          judgment='[{bad: "照度达标即可", good: "晚上看书眼睛不累", reason: cr-probe}]',
          expect_error=False)
data_case("公共禁词同样进数据面核验（不止 domain_extra）",
          identity="本方案依据充分", expect_error=True)
data_case("干净数据放行",
          rule_content="沙发旁留一盏看书的灯", expect_error=False)

print()
if failures:
    for f in failures:
        print("FAIL", f)
    print(f"== 核验回归：{len(failures)} 条不过")
    sys.exit(1)
print("== 核验回归：全绿")
