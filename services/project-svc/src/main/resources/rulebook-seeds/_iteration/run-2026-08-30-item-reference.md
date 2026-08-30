# 真跑存档 · 2026-08-30 · 落点单项引用落地（规则 1.9）：灯光章从 0/6 到过检

> 存档性质：**裁决实施后的验证真跑**。原始 JSON 落 scratchpad（未入仓，同本目录惯例），路径见 §六。
> 前情＝同日 `run-2026-08-30-lighting-rerun.md`：同一域同一份包，改前六轮 **0/6**。

## 一、结论

`lighting@v10` + 规则 1.9 两层模型，经**一键派发**跑全链，**verdict=ok**：8 主张 8 卡、重写 1 轮、
出 1 页，渲染成册。**改前六轮 0/6 的那一章，改后第一次过检**。

两种老失败形态**一次都没复发**：拆 min/max 端点 0 次、自造 lkp- id 0 次。

## 二、模型六轮想说而说不出的那句话，现在写出来了（逐字）

> 所以洗漱台前按 `{lkp-illuminance-bath.vanity}` 做，床头阅读位按 `{lkp-illuminance-bedroom.reading}` 做，
> 厨房操作台按 `{lkp-illuminance-kitchen.task}` 做，不是填标准，是补你家真实条件的缺口。

> 所以起居通用区按 `{lkp-illuminance-living.general}`，沙发阅读位按 `{lkp-illuminance-living.reading}`，
> 餐桌按 `{lkp-illuminance-dining}`，走廊与楼梯按 `{lkp-illuminance-corridor}`，每一处都对准一个具体动作。

对照改前：同样这几条落点，模型六轮共造了 27 种占位符，**27/27 是「真实落点 id + 该落点 value 的一个
真实键」**——它不是不守规矩，是没有合法写法。给了写法，它第一轮就写对了。

**区间落点全部整条引用**（`{lkp-cri-living}`、`{lkp-accent-ratio}`、`{lkp-beam-accent}`），一次没拆——
`min`/`max` 不在项名空间里，**拆的写法在语法上不存在**。结构堵死比纪律禁止有效，这一跑是它的证据。

## 三、渲染出的成品（逐字，读者看到的）

> 所以洗漱台前按 **300 lx** 做，床头阅读位按 **200 lx** 做，厨房操作台按 **300 lx** 做…
> 所以起居通用区按 **100 lx**，沙发阅读位按 **300 lx**，餐桌按 **150 lx**，走廊与楼梯按 **100 lx**…
> 一盏专门打向目标的灯，光束角控制在 **15–24 °**，同时要比周围环境亮 **3–5 ×环境照度**。

**内部标识符零泄漏**：整册 `lkp-` 0 次、项名（general/reading/task/vanity）0 次。

## 四、本跑逮到的两条缺陷（立案，未处置）

### 甲 · 单边界落点的措辞叠字（读者可见，最要紧）

`lkp-cct-variety-max` 是单边界落点（`{max: 3}`，单位「种」）。渲染层按登记形态出「不超过 3 种」，
而写作侧自己也写了边界词与单位，成品逐字是：

> 主旨：`全屋灯光颜色种类不能多于 不超过 3 种 种。`
> 正文：`这个上限 不超过 3 种 是按你实际动线长度和停留节奏定的…`

**两处叠**：边界词（"不能多于" ＋ "不超过"）与单位（"种" ＋ "种"）。这正是
`cr-bound-word-before-placeholder` 立案时那条「不少于不低于 750 mm 一册 7 处」的同一形态——
该判据靠**边界词紧邻占位符**匹配，"不能多于 {…}"隔着字、"上限 {…}"根本不是边界词，两处都漏。
**单位叠字是新形态**（此前只立过边界词那一半）。

处置未定，归收敛专项；**它是当前唯一读者可见的成品缺陷**。

### 乙 · `temporal workflow result` 把空列表打印成 null（工具坑，非契约问题）

用该 CLI 取 pages 喂渲染层，整包解析失败（`assertions`/`locked_text_ids` 收到 `null`）。
核对 Temporal **历史 payload 原文**：成文线发的是 `[]`，**是 CLI 的打印在骗人**。
**机器消费一律取历史事件 payload，不取 `workflow result` 的输出。**

## 四点五、同批跑的造价章：没过，但**不是这条改动的问题**

`budget@v10` 同参一轮：**failed**，重写用满 2 轮，6 条违规——`gate-banned-term`「可能」/「推导」、
`cr-weak-word`、`gate-chinese-numeral`「十年」、`cr-methodology-language`、`gate-sample-verbatim-copy`。
**单项引用相关的违规一条没有**：新机制在这一域同样干净，栽的全是老形态。

其中一条有分量——**`gate-sample-verbatim-copy` 第一次真命中**：

> `card[3] 逐字抄了语域示范「定制柜按投影面积计价，单价 {lkp-price-custom-cabinet}——做到哪一档，差别就在这个区间里。」`

8-29 persona 改源时留的那句「**可抄性是否真解掉未验证**（示范总量刚从 4 涨到 19），下一轮盯
`gate-sample-verbatim-copy`」，今天有答案了：**没解掉**。示范句仍被逐字搬进卡片当结论，
且这次抄的是改源后的新示范。归 persona 那条线，不占本轮射程。

## 五、集成时补掉的一处（跨 session）

渲染层 `RenderPackage` 是 `extra="forbid"`，而并行线当日给数据包加了 `triggeredRulesByDomain`
（获客线「户型特征进报告」）——**整包当场被拒**。已在渲染层声明该字段并写明「只认不渲」与后续路径。
这条只有跑全链才会暴露：两侧各自的测试都是绿的。

## 六、原始产物

```
<scratchpad>/package-v28.json   pages-v28.json   book-v28.html   lighting-v28.json
```

## 七、跑法（含本轮新增的一个入参）

```bash
# 进程组：LiteLLM :4000 / genpipe-http :8104 / genpipe-workflow-worker / reportgen-worker / project-svc :8103
curl -X POST http://127.0.0.1:8103/api/v1/reports -H 'Content-Type: application/json' -d @dispatch.json
# 渲染（--anchor-items 为 v2.8 新增：开集两类项名的展示名，取自 contracts 受控词表）
uv run --directory ~/codes/ishome-reportrender reportrender \
  --pages pages.json --package package.json \
  --anchor-items ~/codes/ishome-contracts/registries/anchor_items.json -o book.html
```
