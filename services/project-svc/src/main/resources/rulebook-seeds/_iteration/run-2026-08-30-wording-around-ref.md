# 真跑存档 · 2026-08-30 · 记号旁边的措辞：叠字判据换载体 + 十二跑取证

> 存档性质：**读者可见缺陷的处置与验证真跑**。原始 JSON 落 scratchpad（未入仓，同本目录惯例）。
> 前情＝同日 `run-2026-08-30-item-reference.md` §四-甲：该缺陷在那一跑立案，是当时**唯一读者
> 可见的成品缺陷**。

## 一、结论

叠字**不再进成品**：判据换载体后，立案的两种形态都被拦住，并出了一册**逐字干净**的成品。

| | 立案那一跑（8-30 上午） | 本轮 |
|---|---|---|
| 成品逐字 | `全屋灯光颜色种类不能多于 不超过 3 种 种。` | `全屋灯光颜色种类按 不超过 3 种 做。` |
| 同册另一处 | `这个上限 不超过 3 种 是按…` | `起居空间的灯光显色能力按 不低于 90 Ra 做。` |
| 边界词叠 | 2 处 | 0 |
| 单位叠 | 1 处 | 0 |

**但这一章的过检率仍是问题**：改后十二跑（同一份输入、同一份 release）只有 1 跑出册，
边界词违规 **9/12 跑**复发——被拦住不等于写得对，见 §四立案。

## 二、换的是什么

`cr-bound-word-before-placeholder`（release 数据，regex）**retire**，语义由 reportgen 两条代码判据
承接：`gate-bound-word-before-ref`（记号前的边界词）、`gate-unit-after-ref`（记号后的单位）。
**用户裁决未变**（2026-08-29 晚"单边界措辞归渲染层"仍然有效），变的是执行它的载体。

理由两条，都出自立案那一跑：

1. **单位那一半 regex 够不着**——要判它得逐字比对这条落点自己的 `unit`，那只有数据包知道。
   同一条纪律的两半分居 release 数据与代码两处，必然改一处忘一处（坑单第 10 条同型）。
2. **词面枚举是形态错误，不是词表不够长**——原 pattern 靠"固定词表 + 紧邻占位符"匹配，
   立案的两句全漏：「不能多于」不在表里、「上限」根本不是它认的边界词。现形态按**词根**
   匹配（"少于"覆盖"不少于/不能少于"）、按**小句**取范围，不设距离阈值。

配套：`pattern` 一并摘掉留进注释——规则层 gate 按设计不看 status（规则 4.10d 明文禁止"修"），
留着 pattern 的 retired 行会继续拦截，等于没退役。旧 release 快照原样保留，回滚＝切回旧 tag。

release 重发六域：`budget@v11 / ergonomics@v10 / lighting@v11 / material@v9 / softdeco@v9 / storage@v9`。

## 三、写作侧同步改了两处（prompt 只是第一道，但这一道必须与门禁同口径）

1. **禁词表退场，改成"这个记号自带什么"**（铁律一：禁止词面不进 prompt）。原 prompt 里列着
   「不少于/不低于/至少/不超过」，模型转头写了「不能多于」和「上限」——**两个都不在那张表上**。
   现形态在每条落点行下逐字写出它自带的东西（单位取自 `unit`、边界说法取自值形态），
   与门禁共用 `bound_phrases` 一份判定。
2. **"边界词就藏在落点自己的题名里"**（坑单第 4 条同型，改后首轮逮到）：`lkp-cct-variety-max`
   题名是「全屋色温种类上限」，模型把「上限」从题名搬进了正文，两轮重写没改掉。处置沿用既有
   那条：撞词的落点逐行点名（词根表 ∩ 题名），并给出接得上的写法。**题名不改**——「上限」
   在题名里是准确的（改说法不改数据）。

## 四、立案：边界词 9/12 跑复发，单位 0/12

十二跑同一份输入、同一份 release，唯一变量是记号自带说法的**给法**（抽象 vs 逐字）：

| 给法 | 跑数 | 出册 | 边界词复发 | 单位复发 |
|---|---|---|---|---|
| 抽象（"自带边界说法"） | 6 | 0 | **5** | 0 |
| 逐字（"自带边界说法「不超过…」"） | 6 | **1** | **4** | 0 |

**两档差别读不出来**（每格 6 跑量不出这个量级，同 persona 示范块 A/B 的既有口径）——不要拿
1/6 当"逐字有效"的证据。能读出来的是另一件事：**单位那一半十二跑一次没复发，边界那一半九跑中**。

机制不是"模型不守纪律"，是**它想写的那句中文里天然带边界词**：`{lkp-cri-living}` 渲出来是
「不低于 90 Ra」，而人话是"显色指数不低于 90"——模型六跑里五跑写出这一句。合法写法存在
（出册那一跑写的是`起居空间的灯光显色能力按 {lkp-cri-living} 做`），但它不是最顺手的那句。

**处置未定，需用户裁决**（三条路，都动到已定裁决或跨仓结构，不自行选）：
- 甲 · 维持现状：判据拦得住，代价是这一章几乎每跑烧一轮重写在这条上；
- 乙 · 写作侧看不见值的字面（把叙事推导那条"结构性看不见值"延伸到写作侧的 min/max 键面）——
  改的是 prompt 里给不给 `{"max": 3}`，不动裁决；
- 丙 · 重开 2026-08-29 晚被否的甲案（渲裸值 + 机检句子须带边界词）——**动裁决**，当时否它的
  理由是"句子漏词时裸数被误读成点值"，而现在有了确定性判据，那条理由是否还成立要重判。

## 四点五、顺带产出：收敛专项的第一批台账（十二跑，按命中跑数）

同一份输入、同一份 release、十二跑，**1/12 出册**（前次"8 跑 1 过"的同量级）。规则层违规按
**命中跑数**（不按条数——同一条判据一跑中两次仍是一跑，触发率要按份读）：

| 判据 | 命中跑数 |
|---|---|
| `gate-bound-word-before-ref` | 9/12 |
| `gate-chinese-numeral` | 6/12 |
| `gate-banned-term` | 5/12 |
| `gate-assertion-not-budgeted` | 4/12 |
| `gate-assertion-unbacked` | 3/12 |
| `gate-number-ref-unused` | 3/12 |
| `cr-methodology-language` | 2/12 |
| `gate-sample-verbatim-copy` | 1/12 |
| `cr-weak-word` | 1/12 |

**收敛专项不必等判官台账**：判官层仍未跑到过，但**规则层这份按跑数读就够开工**——它已经指出
这一章过不了检的头一件事是边界词（§四），第二件是没有落点背书的中文数字。

## 五、同批做掉的一条（它挡着验证）

`gate-chinese-numeral` / `gate-digit-outside-ref` 的打回话补**第二条出路**（坑单第 19 条立案样本）：
原话只说"换 {lkp-*} 占位"，而立案样本「半小时」在本域根本没有能背书它的落点——改前那一跑
两轮重写整个烧在这一条上，它是那跑唯一的违规。现话是"要么写能背书它的落点的记号，要么把这句
改成不带数的说法——**禁的是没有背书的数，不是禁止说这件事**"。改后十二跑里该判据仍会命中
（「三秒」「一成」「四周」），但不再是死路。

## 六、原始产物与跑法

```
<scratchpad>/hist-s1.json  result-s1.json  pages-s1.json  package-s1.json  book-s1.html
# 进程组：LiteLLM :4000 / genpipe-http :8104 / genpipe-workflow-worker / reportgen-worker / project-svc :8103
curl -X POST http://127.0.0.1:8103/api/v1/reports -H 'Content-Type: application/json' -d @dispatch-lighting.json
uv run --directory ~/codes/ishome-reportrender reportrender \
  --pages pages-s1.json --package package-s1.json \
  --anchor-items ~/codes/ishome-contracts/registries/anchor_items.json -o book-s1.html
```

**取数纪律**（沿用同日立案）：机器消费一律取 Temporal **历史事件 payload**，不取
`temporal workflow result` 的打印（它把空列表印成 `null`）。本轮 pages / package 均自
`EVENT_TYPE_WORKFLOW_EXECUTION_COMPLETED` 与 `report-unit-compose` 的入参事件取出。

## 七、本机坑（新一条）

`./gradlew` 默认 JVM 是 Java 8，`bootRun` 起不来（`foojay-resolver` 要 JVM 17+）。
起 project-svc 前先 `export JAVA_HOME=/opt/homebrew/opt/openjdk@21`。
