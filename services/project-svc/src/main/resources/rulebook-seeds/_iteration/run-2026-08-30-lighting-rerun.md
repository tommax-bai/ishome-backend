# 自迭代回路 · 灯光新形态重跑（取证） · 2026-08-30

> 性质：**取证不修复**。灯光章此前两种失败形态（拆 min/max 端点、自造 lkp- id）全部产生于
> 「叙事推导拆两步」落地**之前**；推导步的入参只有落点 id/名字/量纲、看不见值，它对这两种形态的
> 压制从未在灯光上验证过。本轮取的就是这个证。
> 接在 `run-2026-08-29-confession-coupling.md` 之后。**不改源码、不改 prompt、不改种子、不改判据**；
> 所有句子与计数逐字取自真跑输出，判定不在本文。
>
> 代码：ishome-reportgen `d87113b` 原样（假坦白封堵 + 耦合词面转确定性校验 + 撞禁词落点逐行点名）。
> 输入：`package-v9.json`（budget@v9 / ergonomics@v8 / lighting@v9；13 条 lighting 落点；
> `cr-anchor-out-of-scope` 已是 v2/observing 收窄版）。
> 原始 JSON 全部留档（路径见文末）。

## 一、六轮结果：**0/6 过检**，判官层一次都没跑到

同一份包、同一份代码、同参跑六次。前三次是裸驱动；后三次在文档化的 `writer_factory` /
`judge_factory` 注入点外包了一层**纯透传录音器**（原样调用、原样返回，只抄一份入参出参），
用于取失败轮的卡片原文——失败结果不带 `cards`，不录就拿不到逐字证据。

| 跑 | verdict | 主张 | 卡片 | rewrites_used | 违规 | 判官观察 |
|---|---|---|---|---|---|---|
| `compose-lighting-run1.json` | **failed** | 5 | 0 | 2 | 4 | 0 |
| `compose-lighting-run2.json` | **failed** | 6 | 0 | 2 | **15** | 0 |
| `compose-lighting-run3.json` | **failed** | 6 | 0 | 2 | **2** | 0 |
| `compose-lighting-run4.json`（录音） | **failed** | 5 | 0 | 2 | 5 | 0 |
| `compose-lighting-run5.json`（录音） | **failed** | 5 | 0 | 2 | 4 | 0 |
| `compose-lighting-run6.json`（录音） | **failed** | 5 | 0 | 2 | 10 | 0 |

`cards=0` 是 failed 路径的结构事实（`failed()` 不带卡片上抛），不是模型交了空数组——
录音显示每轮**每一稿都产出 5 张卡**（run4/5/6 各 3 稿 × 5 张）。

**判官观察三轮录音全部 `judge_calls=0`**：判官只在规则层放行后才跑，六轮无一稿过规则层，
所以判官从未被问过。灯光域的 4 条 semantic_judge 判据（`cr-anchor-out-of-scope` v2 /
`cr-fabricated-fact` / `cr-self-endorsement` / `cr-undeclared-assertion`，均 observing、均带样例）
本轮**零次送审**。

## 二、拆 min/max 与自造 lkp- **是同一个形态**：落点 id + value 子键

这是本轮最要紧的观察。把六轮所有稿子里"不在本域落点对象内"的占位符全部收齐，共 **27 种**，
逐个回溯归属——**27/27 全部等于「某条真实落点 id」＋「该落点 value 字典里一个真实的键」**，
命中率 100%，无一例外：

| 自造占位符 | 归属真实落点 | value 子键 | 该子键的真值 | 该落点 value 全部键 |
|---|---|---|---|---|
| `lkp-cri-living-min` / `lkp-cri-living.min` | `lkp-cri-living` | `min` | 90 | `min, unit` |
| `lkp-accent-ratio-min` | `lkp-accent-ratio` | `min` | 3 | `max, min, unit` |
| `lkp-accent-ratio-max` | `lkp-accent-ratio` | `max` | 5 | `max, min, unit` |
| `lkp-beam-accent-min` | `lkp-beam-accent` | `min` | 15 | `max, min, unit` |
| `lkp-beam-accent-max` | `lkp-beam-accent` | `max` | 24 | `max, min, unit` |
| `lkp-cct-variety-max.max` | `lkp-cct-variety-max` | `max` | 3 | `max, unit` |
| `lkp-illuminance-kitchen-task` / `.task` | `lkp-illuminance-kitchen` | `task` | 300 | `task, unit, plane, general` |
| `lkp-illuminance-bath-vanity` / `.vanity` | `lkp-illuminance-bath` | `vanity` | 300 | `unit, plane, vanity, general` |
| `lkp-illuminance-living-reading` / `.reading` | `lkp-illuminance-living` | `reading` | 300 | `unit, plane, general, reading` |
| `lkp-illuminance-living.general` | `lkp-illuminance-living` | `general` | 100 | `unit, plane, general, reading` |
| `lkp-illuminance-bedroom-reading` / `.reading` | `lkp-illuminance-bedroom` | `reading` | 200 | `unit, plane, general, reading` |
| `lkp-illuminance-bedroom.general` | `lkp-illuminance-bedroom` | `general` | 75 | `unit, plane, general, reading` |
| `lkp-illuminance-dining-v` / `.v` | `lkp-illuminance-dining` | `v` | 150 | `v, unit, plane` |
| `lkp-illuminance-corridor-v` / `.v` | `lkp-illuminance-corridor` | `v` | 100 | `v, unit, plane` |
| `lkp-cct-living-v` / `.v` | `lkp-cct-living` | `v` | 3000 | `v, unit` |
| `lkp-cct-task-v` / `.v` | `lkp-cct-task` | `v` | 4000 | `v, unit` |
| `lkp-cct-night-v` / `.v` | `lkp-cct-night` | `v` | 2700 | `v, unit` |

`min`/`max` 只是 value 字典里的两个键，与 `task`/`vanity`/`reading`/`general`/`v` 同列。
即：立案时分开记的两种形态，在数据上是一件事——**看见 value 是个字典，就按子键拆成多个占位符**。

### 2.1 新采到的第三种写法：点号式，且它绕开了原有的报错码

六轮里出现了此前未记录的**点号分隔**写法 `{lkp-illuminance-kitchen.task}`。规则层的占位符正则是

> `PLACEHOLDER_RE = re.compile(r"\{(lkp-[a-z0-9-]+)\}")`

**不含点号**。于是点号式根本不被识别为占位符，连带三个后果：

- `gate-number-ref-unresolved` **不响**（它只核已识别的占位符）；
- 剥占位符后残留 `lkp-` 字样 → 响的是 `gate-lkp-identifier-leak`；
- 声明的 refs 找不到对应占位符 → 再响 `gate-number-ref-unused`。

两种写法的报错文本因此完全不同。连字符式拿到的是逐字点名的正解：

> `card[0] 引用 lkp-cri-living-min 不在本域落点对象内——占位符代表整条落点，区间写 {lkp-cri-living} 即可，拆 min/max 会丢掉另一端`

点号式拿到的是：

> `card[0] 正文出现裸 lkp- 标识名——内部落点编号不进客户语域；要引用数字写 {lkp-id} 占位，要说这条没背书就用人话说`
> `card[0] number_refs 声明了却未在正文引用：['lkp-illuminance-bath', 'lkp-illuminance-bedroom', 'lkp-illuminance-kitchen', 'lkp-illuminance-living']——refs 是占位符全集声明；这些落点有值，要么引用它，要么别声明（有值的落点不许说「给不出」，那是被禁止的隐藏）`

**两条反馈都没有说"一个占位符＝整条落点、别拆子键"**，且后一条的措辞（"这些落点有值，
要么引用它，要么别声明"）是为假坦白写的，指向的是隐藏行为，不是拆子键。

### 2.2 逐稿看：形态在**初稿 3/3 复发**，重写把它清掉两次、清不掉两次

| 跑 | 初稿 | 第二稿 | 末稿 |
|---|---|---|---|
| run4 | 连字符式 **14** | 0 | 0 |
| run5 | 点号式 **13** | 点号式 **7** | 0 |
| run6 | 连字符式 **8** | 0 | 点号式 **10**（重写后**新长出来**） |
| run2（未录音，按末稿违规反推） | — | — | 连字符式 **12 条 unresolved** |
| run1 / run3（未录音，按 `gate-lkp-identifier-leak`＝0 且 `gate-number-ref-unresolved`＝0 反推） | — | — | 无 |

可读出的事实三条：

1. **三轮录音的初稿全部复发**（14 / 13 / 8 条），无一例外；
2. 重写循环能压掉它（run4 一轮清干净、run5 两轮清干净），但压不住形态本身；
3. **run6 末稿是重写之后重新长出来的**——第二稿已经干净，第三稿换成点号式又冒出 10 条。

初稿原文（run4 attempt0，两种形态同时在一张卡上）：

> 正文：`厨房操作台和卫生间化妆台这些地方，手在动、东西在眼前，灯光必须够亮才不会看错、拿错、切错；所以操作台和化妆台的照度锚在 {lkp-illuminance-kitchen-task} 和 {lkp-illuminance-bath-vanity}，比屋子里其他地方高得多。起居室沙发旁看书、餐厅吃饭、卧室床头翻页、走廊夜里走动——这些动作眼睛盯得久、距离近，照度就按 {lkp-illuminance-living-reading}、{lkp-illuminance-dining-v}、{lkp-illuminance-bedroom-reading} 和 {lkp-illuminance-corridor-v} 来定`

> 正文：`它要比周围环境亮 {lkp-accent-ratio-min} 到 {lkp-accent-ratio-max} 倍，这个倍数是按你盯多久、看多细定的：太弱，看不出纹理；太强，反而刺眼。光束角控制在 {lkp-beam-accent-min} 到 {lkp-beam-accent-max} 度之间`

> 正文：`起居这片的显色指数下限设为 {lkp-cri-living-min}，这是目前能守住的底线`

末稿点号式原文（run6 attempt2）：

> 正文：`厨房操作台和卫生间化妆台是动手动脚的地方，照度必须达到 {lkp-illuminance-kitchen.task} 和 {lkp-illuminance-bath.vanity}，才能看清刀锋、水渍、睫毛膏刷头这些细节；起居室日常活动用 {lkp-illuminance-living.general}，但沙发旁读书要升到 {lkp-illuminance-living.reading}；餐厅吃饭用 {lkp-illuminance-dining}，卧室走动用 {lkp-illuminance-bedroom.general}，床头看书则要 {lkp-illuminance-bedroom.reading}；走廊只管脚下安全，按 {lkp-illuminance-corridor} 照地面就行。`

> 正文：`起居与卧室用 {lkp-cct-living.v}，晚上回家开灯第一眼是暖的，身体容易放松下来；操作台和镜前用 {lkp-cct-task.v}，光线更中性，切菜不偏色、刮胡子看得清阴影；夜里摸黑走动用 {lkp-cct-night.v}，比主灯更暖、更暗，不打断褪黑素分泌`

### 2.3 与「推导步看不见值」的关系（观察，不下结论）

推导步的入参只有 id/名字/量纲，**主张里六轮零个占位符、零个自造 id**（见第四节）。
形态全部出现在**写作步**——写作请求带的是完整 `anchors`（含 value 字典）。
即：这两种形态发生在能看见 value 的那一步，推导步拆两步与它们不在同一个环节上。

## 三、违规码分布（六轮合计 40 条）

| check | run1 | run2 | run3 | run4 | run5 | run6 | 合计 |
|---|---|---|---|---|---|---|---|
| `gate-number-ref-unresolved` | 0 | **12** | 0 | 0 | 0 | 0 | 12 |
| `cr-bound-word-before-placeholder` | 2 | 2 | 2 | 0 | 0 | 0 | 6 |
| `gate-banned-term` | 0 | 1 | 0 | 2 | 1 | 2 | 6 |
| `gate-number-ref-unused` | 0 | 0 | 0 | 1 | 1 | **4** | 6 |
| `gate-chinese-numeral` | 0 | 0 | 0 | 2 | 1 | 0 | 3 |
| `gate-lkp-identifier-leak` | 0 | 0 | 0 | 0 | 0 | 3 | 3 |
| `cr-weak-word` | 0 | 0 | 0 | 0 | 1 | 1 | 2 |
| `gate-number-ref-undeclared` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `gate-digit-outside-ref` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| **合计** | 4 | 15 | 2 | 5 | 4 | 10 | **40** |

只有两个 `cr-` 码出现，且都是 `regex_deny` 走规则层的（`cr-bound-word-before-placeholder`、
`cr-weak-word`）。`cr-assertion-backed`（active、cross_field）六轮零命中。

**离过检最近的一轮是 run3：只剩 2 条**，且两条是同一个码（`cr-bound-word-before-placeholder`，
card[0] 与 card[1] 各一条）。

## 四、主张逐字（推导步产出，六轮全列）

六轮主张**零禁词、零占位符、零自造 id**。落点覆盖：run1 只覆盖 7/13（六条 `lkp-illuminance-*`
整批漏掉），其余五轮 **13/13 全覆盖**。

**run1（5 条，漏 6 条落点）**

> `厨房操作台和卫生间镜前的灯光，要让颜色看起来最接近白天自然光下的样子，这件事比全屋灯光用几种温度更重要。` ← `lkp-cct-task`, `lkp-cri-living`
> `起居室和卧室的灯光温度是一回事，不能拆成两个地方分别定；你晚上在沙发上看书、睡前躺在床上刷手机，眼睛经历的是同一种光感。` ← `lkp-cct-living`
> `夜里起床走动时经过的走廊、楼梯、卫生间门口这些地方，灯光温度必须单独定，不能和白天用的光混在一起，否则会干扰身体判断时间。` ← `lkp-cct-night`
> `全屋最多能用几种不同的灯光温度，这件事现在还不能给你一个确定的数，它取决于你最终选的灯能不能在不同区域之间自然过渡，得等你挑好几款灯之后才能确认。` ← `lkp-cct-variety-max`
> `重点打亮某样东西的灯光，比如餐桌上的菜、墙上的画、厨房台面上的刀具，它的亮度和周围相比要高出多少，这件事有明确要求；但光束角宽窄——也就是那圈亮光是胖还是瘦——得看你实际挂灯的位置和想突出的东西大小来定，现在没法统一说。` ← `lkp-accent-ratio`, `lkp-beam-accent`

**run2 与 run3（6 条，逐字相同——同参两跑推导步产出完全一致）**

> `厨房和卫生间这两处动手的地方，灯光要足够清楚地照出你手上的动作和台面的细节，不能让颜色看起来失真` ← `lkp-illuminance-kitchen`, `lkp-illuminance-bath`, `lkp-cct-task`, `lkp-cri-living`
> `起居室和卧室的灯光，重点不是亮不亮，而是让你放松下来、看清书页或人脸时，颜色是自然的、舒服的` ← `lkp-illuminance-living`, `lkp-illuminance-bedroom`, `lkp-cct-living`, `lkp-cri-living`
> `从床边到卫生间这段夜里摸黑走的路，灯光要够用但不能刺眼，色温要低到不干扰睡意` ← `lkp-illuminance-corridor`, `lkp-cct-night`
> `餐厅的灯光要能让你看清饭菜的颜色和质地，同时让坐在对面的人脸不显得暗沉或发青` ← `lkp-illuminance-dining`, `lkp-cct-task`
> `全屋用几种色温，这件事现在还给不出确定的数——它取决于你以后会不会换灯、愿不愿意在不同房间之间接受一点色差` ← `lkp-cct-variety-max`
> `重点打亮某样东西时，要比周围亮多少、光束收多窄，这件事现在还给不出确定的数——它取决于你最终选的是哪几件要突出的东西，以及它们离灯有多远` ← `lkp-accent-ratio`, `lkp-beam-accent`

**run4 与 run6（5 条，逐字相同）**

> `厨房和卫生间这两处动手动脚的地方，灯光要足够清楚地照出你手边的东西，不能靠猜；起居室、餐厅、卧室和走廊的亮度则按各自最常做的动作来定——不是按'看起来亮堂'，而是按'做这件事时眼睛不累'。` ← 六条 `lkp-illuminance-*`
> `起居空间的灯光必须让颜色看起来接近你在白天自然光下看到的样子，尤其是沙发旁放书、挑衣服、看画的时候；这个要求只在起居空间提，别的地方不强求。` ← `lkp-cri-living`
> `重点照明——比如照餐桌中心、照挂画、照台面某一段——要比它周围亮得多，而且光要聚，不能散；这个'亮得多'和'聚'的程度，是按你要看清什么、盯多久来定的，不是按房间大小或装修风格。` ← `lkp-accent-ratio`, `lkp-beam-accent`
> `全屋用几种色温，这件事现在还给不出可靠的说法；它取决于你以后会不会换灯、家里有没有人对光特别敏感、以及你愿不愿意为不同功能留出不同的'光感节奏'——这些信息这轮还没拿到。` ← `lkp-cct-variety-max`
> `起居与卧室、操作台与镜前、夜灯动线这三处地方的色温，各自独立决定：起居和卧室用一种，操作台和镜前用另一种，夜里摸黑走动用第三种；它们之间不互相牵制，也不需要统一成一个调子。` ← `lkp-cct-living`, `lkp-cct-night`, `lkp-cct-task`

第五条这句"各自独立决定""不互相牵制"是**耦合边界的反面用法**：v2.5 禁的是编造耦合，
这里是明确否认耦合——同属"关系不由 LLM 决定"的射程，形态是否合规不在本文判定，登记备查。

**run5（5 条）——末条把造价域的缺口讲进了灯光章**

> `厨房和卫生间这两处动手动脚的地方，灯光要足够清楚地照出你手边的东西，不能靠猜；起居室、餐厅、卧室和走廊的亮度则按各自最常做的动作来定——不是统一调亮，也不是统一调暗。` ← 六条 `lkp-illuminance-*`
> `重点照明不是把灯装得更亮就行，而是要让被照的东西在周围环境里明显跳出来——这个'跳出来'的程度，我们按你实际想看清什么来定，不是按房间名字来定。` ← `lkp-accent-ratio`, `lkp-beam-accent`
> `全屋用几种色温，这件事我们这轮没法直接给你一个数——它取决于你愿不愿意为不同地方换不同感觉的光而多装几路开关，也取决于你以后会不会觉得某处的光太冷或太暖；我们只告诉你现在能确认的：操作台、镜前要用一种色温，起居和卧室用另一种，夜灯动线再用一种。` ← `lkp-cct-living`, `lkp-cct-night`, `lkp-cct-task`, `lkp-cct-variety-max`
> `起居空间的灯光要让你看清颜色本来的样子，比如衣服、画、沙发面料——这个'看清颜色'有最低要求，低于它，很多细节就容易看错；其他地方没设这个底线。` ← `lkp-cri-living`
> **`预算由哪几盏灯决定，这件事现在还不能算出来。`** ← `anchors=[]`

末条来自数据包的缺口清单。包里 `gaps` 只有一条，且是**造价域**的落点：

> `[{"lkpId": "lkp-budget-driver", "reason": "formula_not_implemented", "detail": "占比最高且量可变的分项（通常为定制延米或主材档位）"}]`

而 activity 下发给推导步的是 `gaps=package.gaps`（`ReportDataPackage.gaps` 是包级 `list[GapRecord]`，
无域过滤）。**缺口清单不按域投影，灯光章的推导步拿到的是造价章的缺口**，并据此写出了一条
零落点的坦白主张，还一路进了末稿卡片（见下节 run5 card[4]）。

## 五、卡片逐字

六轮无 ok 轮。以下取三个录音轮的**末稿**（即产生上表违规的那一稿）。

### run4 末稿（5 卡，栽 5 条）

> `[card 1]` refs=`['lkp-cri-living']` assertions=`['显色指数下限']`
> 主旨：`起居空间的灯光必须让颜色看起来接近你在白天自然光下看到的样子，尤其是沙发旁放书、挑衣服、看画的时候。`
> 正文：`沙发旁翻书页、挂画框上色、衣柜前选衣服——这些事都依赖你对颜色的真实判断；如果灯光把白衬衫照成灰黄、把蓝窗帘照成紫灰，眼睛就要不断校正，很快发酸；所以起居这片的显色指数不能低于 {lkp-cri-living}，这是保证颜色不走样的底线。`

这张卡是六轮里**最像样的一张**：占位符是整条落点、有断言声明、后果是生活语言。栽在一个禁词「保证」上。

> `[card 2]` refs=`['lkp-accent-ratio', 'lkp-beam-accent']` assertions=`['重点照明照度倍数']`
> 主旨：`重点照明——比如照餐桌中心、照挂画、照台面某一段——要比它周围亮得多，而且光要聚，不能散。`
> 正文：`照餐桌中心那块肉、照墙上画框的边线、照砧板上切到一半的姜片——这些动作需要你盯住一个点看几秒甚至几十秒；如果光不够亮、又散开去，眼睛就得用力聚焦，容易干涩酸胀；所以这类灯要比周围环境亮 {lkp-accent-ratio}，光束角收窄到 {lkp-beam-accent}，才能把光稳稳钉在你要看的地方。`

对照 2.2 节同一张卡的初稿——`{lkp-accent-ratio-min} 到 {lkp-accent-ratio-max} 倍` 已被重写成
整条落点 `{lkp-accent-ratio}`，**"一个占位符＝整条落点"在这一处被重写循环纠正了**。

> `[card 4]` refs=`['lkp-cct-living', 'lkp-cct-night', 'lkp-cct-task']` assertions=`[]`
> 主旨：`起居与卧室、操作台与镜前、夜灯动线这三处地方的色温，各自独立决定：起居和卧室用一种，操作台和镜前用另一种，夜里摸黑走动用第三种。`
> 正文：`起居和卧室用 {lkp-cct-living}，回家第一眼是暖的，放松下来不刺激；操作台和镜前用 {lkp-cct-task}，切菜、剃须、上妆时看得清细节；夜里摸黑去卫生间的路，用 {lkp-cct-night}，不惊醒也不刺眼；这三组互不牵制，也不需要统一成一个调子。`

登记一条：这张卡 `assertions=[]`，而 persona 的 `色温分区` 谓词 requires 三条
（`lkp-cct-living` / `lkp-cct-task` / `lkp-cct-night`），其中 **`lkp-cct-night` 是 draft /
REFERENCE_ONLY**——声明了就会被 `cr-assertion-backed`（active）拦下。不声明则机检无话可说
（"是不是判断句"是语义判断），而句子写的是"各自独立决定"「用一种」的确定口吻。
这正是 `cr-undeclared-assertion`（observing）的射程——**而判官本轮一次都没跑到**。

### run5 末稿（5 卡，栽 4 条）

> `[card 0]` refs=六条 `lkp-illuminance-*` assertions=`[]`
> 主旨：`厨房操作台和卫生间镜前的灯光要亮到能看清手边每样东西，起居室、餐厅、卧室和走廊则按你最常做的动作来定亮度。`
> 正文：`厨房操作台需要照得清楚，才能切菜不切到手；卫生间镜前要够亮，才能看清脸上的细节；这两处的亮度锚在 {lkp-illuminance-kitchen} 和 {lkp-illuminance-bath}。起居室的亮度锚在 {lkp-illuminance-living}，是为坐着聊天、看手机、偶尔读书留出的余量；餐厅锚在 {lkp-illuminance-dining}，刚好让饭菜颜色看得真实；卧室锚在 {lkp-illuminance-bedroom}，既不刺眼也不昏暗，躺下前翻几页书也够用；走廊锚在 {lkp-illuminance-corridor}，确保夜里走动不绊脚。这些数字不是随便选的——它们对应你真正伸手、抬头、落座、起身时眼睛所在的位置，而不是天花板或地板的平均值。`

六个占位符全部是整条落点，无一拆子键。末句 `这些数字不是随便选的` 逐字落在
`cr-self-endorsement`（observing、semantic_judge）的 requirement 措辞上——
> `任何'这个数不是凭感觉/不是随便定的''是根据……确定的'式自我背书都算违规`

**规则层判不出，判官本轮没跑。** 登记。

> `[card 4]` refs=`[]` assertions=`[]`
> 主旨：`预算由哪几盏灯决定，这件事现在还不能算出来。`
> 正文：`哪几盏灯最终影响总预算，要等你选好具体型号、确认安装方式、看过实际布线条件之后才能确定。目前这一步还没走到，所以没法提前告诉你哪几盏是关键支出项。`

造价域缺口（`lkp-budget-driver`）在灯光章成卡的终点（第四节）。零 refs、零 assertions，
机检无处可拦；它讲的是**别的域**的事。

### run6 末稿（5 卡，栽 10 条——全轮最多）

> `[card 1]` refs=`['lkp-cri-living']` assertions=`['显色指数下限']`
> 主旨：`起居空间的灯光必须让颜色看起来接近你在白天自然光下看到的样子，尤其是沙发旁放书、挑衣服、看画的时候。`
> 正文：`起居空间是你白天活动时间最长、对颜色判断最敏感的地方：挑衬衫、翻绘本、看挂画、甚至分辨药盒上的字，都依赖灯光还原真实色彩。这个能力由显色指数决定，{lkp-cri-living.min} 是能守住这些判断不出错的底线——低于它，白衬衫可能泛黄，青菜看着发灰，画框阴影里的笔触会糊成一片。`

一张卡同时栽四条：点号式子键（→ `gate-lkp-identifier-leak` + `gate-number-ref-unused`）
＋ 禁词「可能」（→ `gate-banned-term` + `cr-weak-word`）。

> `[card 2]` refs=`['lkp-accent-ratio', 'lkp-beam-accent']` assertions=`['重点照明照度倍数']`
> 主旨：`重点照明——比如照餐桌中心、照挂画、照台面某一段——要比它周围亮得多，而且光要聚，不能散。`
> 正文：`重点照明不是'多加一盏灯'，而是让视线焦点比周围亮出 {lkp-accent-ratio}，这样眼睛才不用费力搜寻；同时光束角收窄到 {lkp-beam-accent}，光斑才不会漫开、把画框外的墙也洗亮——太散就失去聚焦意义，太窄又照不全整本书或整盘菜。`

这张卡零违规。

## 六、判官观察：**本轮无观察可报**

六轮 `observations` 全为空数组，三轮录音 `judge_calls=0`。原因是结构性的：判官只在规则层
放行后才跑，六轮无一稿过规则层。**"判官编依据引输入面之外的标准号"这一项本轮无样本**——
不是判官这次没编，是判官这次没被问。

## 七、禁词命中

灯光域**生效禁词 25 个**，不是 21 个：包级 `bannedTermsByDomain.lighting` 21 个

> `一定不会、也许、作业范围、依据、保证、免责、可能、宜、尽量、建议考虑、承诺、推导、无风险、本方案、由业主自行承担、确保效果、经严谨、综合考量、视情况、责任、验收标准`

＋ persona `bannedTerms.domain_extra` 4 个（`collect_banned_terms` 会并入）

> `照度、显指、光通量、眩光值`

六轮共 6 次 `gate-banned-term`，撞到的只有三个词：**照度 ×2、保证 ×2、可能 ×2**。原句逐字：

> run4 card[0] 「照度」：`厨房操作台面和卫生间化妆台需要比屋内其他地方更亮，因为切菜、刮胡子、涂护肤品这些动作容不得模糊——它们分别按 {lkp-illuminance-kitchen} 和 {lkp-illuminance-bath} 的照度来配灯`
> run6 card[0] 「照度」：`厨房操作台和卫生间化妆台是动手动脚的地方，照度必须达到 {lkp-illuminance-kitchen.task} 和 {lkp-illuminance-bath.vanity}，才能看清刀锋、水渍、睫毛膏刷头这些细节`
> run4 card[1] 「保证」：`所以起居这片的显色指数不能低于 {lkp-cri-living}，这是保证颜色不走样的底线`
> run5 card[3] 「可能」：`起居室的灯光必须达到 {lkp-cri-living} 的显色能力，否则浅灰沙发可能看起来像蓝灰，牛仔裤洗后掉色也难分辨`
> run6 card[1] 「可能」：`这个能力由显色指数决定，{lkp-cri-living.min} 是能守住这些判断不出错的底线——低于它，白衬衫可能泛黄，青菜看着发灰，画框阴影里的笔触会糊成一片`
> run2 card[1] 「保证」（未录音，仅有违规行）：`card[1] 命中禁词「保证」`

「照度」的来源与 8-29 存档记的同一条：**它就写在落点自己的名字里**——13 条灯光落点有 **7 条**
名字含「照度」（`重点照明照度倍数`、`卫生间/卧室/走廊与楼梯间/餐厅/厨房/起居室照度标准值`），
且 persona `assertion_budget` 的谓词名 `重点照明照度倍数` 也含它。写作步每读一次落点清单、
每声明一次这条断言预算，就在读这个禁词。

## 八、其余采到的三条（登记，未处置）

1. **`gate-chinese-numeral` 3 次命中，2 次是子串误报**。判据是"数词+量词"三形态，量词表含 `成`、`周`：
   > run4 card[4]「一成」← 原句 `这三组互不牵制，也不需要统一成一个调子`（命中的是"统**一成**一个"）
   > run5 card[1]「四周」← 原句 `餐桌吊灯中心要比四周亮出同样倍数，才能让食物看起来有食欲`（"四周"＝周围，不是四个星期）
   > run4 card[2]「十秒」← 原句 `这些动作需要你盯住一个点看几秒甚至几十秒`（形态①正常命中，但语境是修辞时长不是选型数字）

   量词表的注释本身写着"按真实误报补，不做通用中文数字解析"——这三条是新的真实误报样本。

2. **`cr-bound-word-before-placeholder` 是前三轮的唯一杀手（3/3 都栽它），后三轮零命中**。
   它的 pattern 是
   > `(不少于|不低于|不小于|不超过|不高于|不大于|至少|最少|最多)\s*[（(]?\{lkp-`

   run4 card[1] 写的是 `显色指数不能低于 {lkp-cri-living}`——「不**能**低于」中间隔了一个字，
   pattern 未匹配，同一个意思的另一种写法就过去了。同一份包同一份代码，边界词栽不栽是随机的。

3. **收敛率数据点**：灯光域同包同码同参 **6 跑 0 过**。三轮录音每轮都用满 2 轮重写
   （`rewrites_used=2`，即每轮 3 稿），末稿残余 2~10 条不等，且**每轮死在不同的码上**
   （run1 边界词+裸数字；run2 拆子键 12 条；run3 只剩 2 条边界词；run4 禁词+中文数字+unused；
   run5 中文数字+弱词+unused；run6 点号式+禁词+unused+leak）。与 8-29 ergonomics 记的
   "8 跑 1 过、各死在不同 1~3 条残余"是同一形态，灯光更难：残余条数更多、且**新形态还在长**（点号式）。

## 九、异常

无。网关 `http://localhost:4000/health/liveliness` 应答 `"I'm alive!"`；六轮无报错、无超时、
无解析失败；单轮实测耗时 59 秒（run1 计时），录音轮因多带一层序列化略长，量级相同。

判官台账 `REPORTGEN_JUDGE_LEDGER` 已按要求设到 `judge-ledger-lighting.jsonl`，
**六轮跑完文件未被创建（0 行）**——`append_judge_ledger` 在 `run is None` 时直接返回，
而判官从未被调用。这不是台账故障，是第一节那件事的同一个事实的另一面。

## 十、原始产物留档（scratchpad，非仓内）

```
/private/tmp/claude-501/-Users-baitianxing-codes-ishome/24825b5e-6f38-46b7-81fe-5ab7413f648b/scratchpad/
  package-v9.json                     输入包（自上游 session 拷入）
  run_lighting.py                     裸驱动（前三轮）
  run_lighting_traced.py              录音驱动（后三轮，纯透传 writer/judge 包装）
  compose-lighting-run1..6.json       六轮 activity 返回值
  trace-lighting-run4..6.json         三轮逐稿录音（每稿的 feedback_in / claims_in / cards_out）
  final-cards.txt                     三轮末稿卡片文本
  judge-ledger-lighting.jsonl         未生成（判官零调用）
```
