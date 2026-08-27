# 知识资产种子 · 六域第一批编译

> 形态与纪律：《装修报告生成规则规范》v2.1 —— 规则 4.9（五形态）/ 4.10（治理头）/ **4.10a（可核性门）** /
> 4.12（种子文件入口，灌库后 DB 唯一真相）/ 4.13-4.15（persona）/ **4.16-4.18（三条回路与冷启动）** / 1.7-1.8（命名）。
> 产出方式：系统编译（规则 4.5）。**编译产出结构，不产出可信度**——见下"当前状态"。

## 目录

```
_common/            跨域共用（编译期），灌库时物化进每个域的 release
  checks.yaml       6 条跨域机检规则（弱词/方法论用语/量纲/示意图禁数/断言背书/draft 不进 PAID）
  banned-terms.yaml 跨域禁词表（弱词/方法论/越权承诺/责任话术）
lighting/           照明     persona + parameters(8)  + rules(5) + checks(3)
ergonomics/         人体工学 persona + parameters(23) + rules(5) + checks(3)
material/           用材     persona + parameters(3)  + attributes(3) + rules(4) + checks(3)
storage/            收纳     persona + parameters(6)  + attributes(4) + rules(5) + checks(2)
softdeco/           色彩软装 persona + parameters(4)  + attributes(2) + rules(3) + checks(4)
budget/             造价     persona + parameters(4)  + attributes(4) + rules(4) + checks(4)
```

合计 30 个 YAML、约 690 行；parameter 48 条、rule 26 条、attribute 13 条、check 19 条 + persona 6 份。

## 编译中产生的两个设计决定（原规范未覆盖，需回写）

1. **跨域资产的存在形式**：弱词、方法论用语、量纲翻译这类判据是跨域的，但 release 粒度是 domain（规则 4.12）。
   采用：`_common/` 只在**编译期**存在，灌库时**物化**进每个域的 release —— release 保持自包含、可独立回滚，
   代价是同一条判据在六个 release 里各存一份（可接受：它们本就应随域各自演进）。
2. **attribute 的 entity_type 分布**：material=`material`、storage=`storage_item`、softdeco=`color`、budget=`work_item`。
   四种 schema 完全异构，印证规则 4.12"以 entity_type + JSONB 承载、不按实体类建表"是对的。
   各 `attribute_schema` 草案就在对应 attributes.yaml 里，定稿后移交 contracts。

## 当前状态：几乎全部 draft

按规则 4.10a，`calibrated` 的第一项是 **source 必须能回到原文位置**。本批数值来自行业通行做法与内部规范转写，
**没有一条接上外部标准原文**，因此绝大多数 `calibration: draft`，只能降档呈现，不得作判断句背书。

唯一 `calibrated` 的是 budget 域三条 `tier-mandatory` **纪律型**条目（只出区间/禁植商品/过期降档）——
它们的 source 是内部裁决而非外部数据，不需要外部核验。**这划出了一条边界：纪律可以内部定，数值不行。**

## 获取回路第一批目标（规则 4.16，按优先级）

| # | 要取什么 | 锚住哪些条目 |
|---|---|---|
| 1 | **公开装修行情 + 各地信息价**（≥2 源，带城市档与 effective） | `budget/attr-price-*` 全部；`lkp-budget-share`。造价是唯一"没数据就整章降档"的域 |
| 2 | 厂商公开参数（灯具色温/显指/光束角；布料耐磨色牢度；板材指标） | `lighting/lkp-cct-*`、`lkp-cri-living`、`material/attr-material-*` |
| 3 | GB 50034 建筑照明设计标准（住宅照度表） | `lighting/lkp-accent-ratio` |
| 4 | 住宅电气设计规范 | `lighting/rule-mandatory-lighting-bedroom-dual-control`、`ergonomics/rule-personal-ergo-child-socket-guard`（决定这两条留 tier-mandatory 还是降级） |
| 5 | 人因工程数据源 | `ergonomics/` 全部 23 条 |
| 6 | 色卡体系（NCS/RAL 或自有） | `softdeco/attr-color-*`；同时决定色彩命名 vocabulary |

**注意 `storage/lkp-storage-density-baseline`（收纳延米密度基准）无任何外部真源**——它的转正路径只能是埋点信号
（规则 4.10a 升级路径），是六域里唯一天生只能靠回流的核心参数。

## 灌库

`svc_rulebook` 六表族尚未建（Flyway 迁移未写）。在那之前本目录是形态的唯一定义，可 diff、可评审。
灌库后 DB 为唯一真相，本目录封存为审计记录，不做双向同步（规则 4.12）。

## 尚未编译的

- `template`（句式库）与 `vocabulary`（词表）两形态：按规则 4.17 由**自迭代回路自产**，不在本次人工编译范围。
  冷启动期（规则 4.18）如需初始句式，走"人驱动 AI 起草 → 过种子集回归 → 观察态"路径（规则 4.19），
  产物仍是 release 数据，不是挂在 prompt 上的模板文本。
- 照明域的 attribute（灯具参数卡）：规范 §5.1 未单列，选型参数直接进 art-purchase-checklist，暂不建表。
