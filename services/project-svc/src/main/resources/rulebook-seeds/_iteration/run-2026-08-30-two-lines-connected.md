# 真跑存档 · 2026-08-30 · 两条线接通（求值线 → 成文线，一键派发）

> 存档性质：**工程接通的真跑证据 + 人体工学域三轮收敛数据**。
> 原始产物 JSON 落 scratchpad（未入仓，同本目录既有惯例），路径见 §五。

## 一、接通形态（裁决③④ 的实装）

```
POST /api/v1/reports (project-svc :8103)
  → 铸 report_id（ULID 26 字符，铸在派发之前——重试才有幂等键可握，裁决③）
  → 求值线同步求值（不进队列）+ 必挂锁定文案并集（调用方 art- 传入 ∪ 求值线派生）
  → POST /api/v1/genpipe/reports (genpipe-svc :8104，裁决④ HTTP 通道，不用 Java Temporal SDK 直连)
  → ReportComposeWorkflow (队列 genpipe-workflows)
  → report-unit-compose × N 域 (队列 reportgen-activities) → report-page-assemble → report-book-check
```

**接通前的实际状态**（本轮才发现）：编排侧 `POST /api/v1/genpipe/reports` 路由**早就写好了**，但全仓
没有任何 `FastAPI()` 把 router 挂上去——端点处于"声明态"，curl 打过去连不上，而单测只测纯函数、一路全绿。
**路由写了 ≠ 端点可达**，这条只能由挂载后的真实请求断言（已补 `tests/test_genpipe_http.py`）。

## 二、首跑逐字（report_id `01M18E1YGKVQZGCCNB0PCY4K7B`）

派发响应 `HTTP 202`：

```json
{"reportId":"01M18E1YGKVQZGCCNB0PCY4K7B",
 "workflowId":"report-compose-01M18E1YGKVQZGCCNB0PCY4K7B",
 "runId":"01a050e1-03d4-7c0d-902e-b1444152463a"}
```

从 Temporal 历史里取出编排侧**实收**的报文（即跨语言那一跳的线上事实）：

```
report_id  : 01M18E1YGKVQZGCCNB0PCY4K7B
domains    : ['ergonomics']
evaluatedOn: '2026-08-30'        ← ISO 日期串
lockedTextsByDomain: {'ergonomics': ['DISCLAIM_APPENDIX']}   ← 调用方 art- 传入那半，并集生效
落点数 23 | entitlement PAID | cityTier 一线
```

### 立案：`evaluatedOn` 差点以 `[2026,8,29]` 出线

用默认 ObjectMapper 时 `LocalDate` 序列化成**时间戳数组** `[2026,8,29]`，而 contracts schema 要的是
ISO 日期串（成文线侧 `evaluated_on: str | None`，收到数组即整包解析失败）。**被出站单测逐字断言拦下**。

处置：出站客户端**自带序列化口径**（`JsonMapper` + `JavaTimeModule` + 关 `WRITE_DATES_AS_TIMESTAMPS`），
不吃环境里那个 ObjectMapper。理由：跨仓契约面的字段形态不该随 `spring.jackson.*` 一改就漂——那是全局
配置，改它的人看不见这条线；漏发现的形态是下游整包解析失败，离改动很远。

## 三、人体工学域三轮收敛（同一份输入、同一份代码、同参）

| 轮 | verdict | 阶段 | 重写 | 违规 | 逐条 |
|---|---|---|---|---|---|
| 1 | failed | unit-compose | 2 | 6 | `gate-digit-outside-ref`(裸数字)／`cr-bound-word-before-placeholder`／`gate-chinese-numeral`「九十度」／`gate-banned-term`「依据」／`cr-methodology-language`／`gate-chinese-numeral`「六十度」 |
| 2 | failed | unit-compose | 2 | 2 | `gate-banned-term`「保证」／`gate-chinese-numeral`「半小时」 |
| 3 | failed | unit-compose | 2 | 3 | `gate-banned-term`「可能」／`cr-weak-word`／`gate-chinese-numeral`「半小时」 |

**0/3 过检**。同一域 8-29 17:44 曾出过合格成品（23 卡），本轮同参三跑全灭——**收敛率问题在接通之后
第一次以"同一个按钮连按三次"的形态被观测到**，不再依赖手工拼跑的记忆。

三轮判官层**一次都没跑到**（规则层先拦下），故 `REPORTGEN_JUDGE_LEDGER` 三轮零行——
**台账攒不够数不是台账坏了，是文稿没活到判官那一步**。收敛专项要读台账，得先有能过规则层的文稿。

### 立案：`gate-chinese-numeral` 对「半小时」给的是做不到的指令

第 2、3 轮**同一个词面**复发。打回提示逐字是：

> `card[4] 正文以中文数字写数（「半小时」）——换 {lkp-*} 占位；数字纪律管的是数不是字形`

但**本域没有"半小时"这条落点**，模型换不成占位符——它唯一的合法动作是整句不提这个时长。
提示要求的动作在数据面上不存在，于是同一句连吃两轮。

与灯光"自造 `lkp-illuminance-living-general`"**同族**：都是模型**无路可走**时的产物，不是不守规矩——
灯光那边是多键 value 没有引用语法，这边是根本没有对应落点。**打回提示只有在"照做得到"时才是打回，
否则就是把重写轮数烧掉。** 处置未定，随收敛专项一并看。

## 四、未由本轮证明的一段

三轮都止步 unit-compose，故 `report-page-assemble` → `report-book-check` **这一段没被真产物走过**。
它的覆盖来自 aipipe `tests/test_temporal_integration.py`（真连 Temporal、reportgen 侧打桩）9 条全绿，
含 fan-out→装配→册检、单元失败即整册失败、册检失败不回 pages 四种走法。
**缺的是"真 reportgen 回合格卡片"那一次**，卡在收敛不在接线。

## 五、原始产物

```
<scratchpad>/e2e-run2.json  e2e-run3.json      # 编排侧 workflow result 全文
<scratchpad>/dispatch.json                     # 派发请求体（匿名画像 + 必挂集）
```
首轮结果经 `temporal workflow result --workflow-id report-compose-01M18E1YGKVQZGCCNB0PCY4K7B` 复取。

## 六、跑起来要开的进程（缺一即断在不同地方）

```bash
# 1) LiteLLM 网关 :4000        infra/litellm/run-dev.sh
# 2) genpipe 入站面 :8104      cd ishome-aipipe && uv run genpipe-http
# 3) genpipe workflow worker   cd ishome-aipipe && uv run genpipe-workflow-worker
# 4) reportgen activity worker cd ishome-reportgen && uv run reportgen-worker
#    （需 LITELLM_BASE_URL / LITELLM_API_KEY / TEMPORAL_ADDRESS / REPORTGEN_JUDGE_LEDGER）
# 5) project-svc :8103         cd ishome-backend && ./gradlew :services:project-svc:bootRun
curl -X POST http://127.0.0.1:8103/api/v1/reports -H 'Content-Type: application/json' -d @dispatch.json
```
