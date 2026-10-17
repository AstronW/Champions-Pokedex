# 重构审计与台账

审计时点：`881960e`（2026-10-17）。范围：`app/src/main`、`app/src/test`、`app/src/androidTest`；`scripts/` 与生成物不在范围内。

## 基线

| 项 | 命令 | 结果 |
|---|---|---|
| JVM 单元测试 | `sh ./gradlew :app:testDebugUnitTest` | 6 个类、17 个用例全部通过，合计约 0.63 s（`app/build/test-results/testDebugUnitTest/*.xml` 的 `time` 求和） |
| lint | `sh ./gradlew :app:lintDebug` | `No issues found.` |
| 规模 | `git ls-files 'app/src/main/*.kt' \| xargs wc -l` | 约 6.8k 行，同属一个包 `dev.champions.dex`（`components/` 另成一包） |

## 架构结论

对这个体量来说，现有架构是合理的：单模块、单个 `DexViewModel`，用 Navigation 3 返回栈保存每个条目的状态；数据解析、计算与队伍分析都是带单元测试的纯函数，界面只读不可变的 `Dex`。不需要再引入 Repository/UseCase 分层或依赖注入。

主要债务集中在下面几处，没有结构性缺陷：

1. **文件内容与文件名不符**：`ToolsScreen.kt` 里装着属性克制、速度线、队伍构建、收藏对比 4 个页面；`DexRepository.kt` 里装着 Repository、`DexViewModel` 和 `SpriteCache`。
2. **JSON 读取辅助函数各写各的**：`DexRepository.kt`、`Mechanics.kt` 各定义了一套私有扩展，`CalculatorData.kt`、`Team.kt` 里也有内联的 `(0 until length())`。
3. **界面开关与计算内核不一致**：缺陷 D1，见下表。

计算器请求用的是 JSON 字符串（`rememberSaveable` + `JSONObject`），这和 `scripts/calculator/fixtures.json` 的请求格式是同一份合同，属于**刻意设计**，不改成类型化模型。

## 台账

状态：`Proposed` / `Approved` / `In progress` / `Blocked` / `Superseded` / `Done` / `Rejected` / `Deliberate`。

| ID | 类别 | 要保留的行为 / 问题 | 证据 | 规范归属 | pin | 验证 | 依赖 | 状态 |
|---|---|---|---|---|---|---|---|---|
| C1 | 特征测试 | `parseMechanics` 对真实 `mechanics.json` 的解析结果（分类、关系、状态、引用都非空，别名合并按 key 唯一） | `grep -n parseMechanics app/src/test` 无结果，现有测试都传 `Mechanics()` | `app/src/test/.../MechanicsParseTest.kt` | 无 | 档 2 | — | Done |
| D1 | 缺陷 | 计算器“单侧状态”提供了蓄电池、能量点、钢之意志、花之礼 4 个开关，但无论怎么切换都不影响结果 | `grep -n "isBattery\|isPowerSpot\|isSteelySpirit\|isFlowerGift" vendor/damage-calc/src/mechanics/champions.ts` 无结果；`CalculatorEngine.makeSide` 也不读这 4 个键 | 引擎读取的键集合 `CalcSideFlags`（`CalculatorEngine.kt`），界面列表只能从这个集合里取 | fixtures 请求里含这些键（引擎忽略它们，不受影响） | 档 2 + 锁定测试 | — | Done |
| D2 | 缺陷 | `rememberWiki` 解析失败时把空列表写进缓存，之后本次运行中这一页永远显示为空 | `WikiContent.kt` 中 `getOrElse { emptyList() }.also { WikiCache.pages.put(...) }` | `rememberWiki` | 无 | 档 1 | — | Done |
| R1 | 去重 | 所有 JSON 数组读取结果不变 | 上面 4 个文件各有一套写法（`grep -n "0 until .*length()" app/src/main`） | 新文件 `Json.kt` 里的 internal 扩展 | 测试只调用 `parseDex` / `CalculatorCatalog` / `decodeTeam`，不碰这些私有辅助函数 | 档 2 | C1 | Done |
| R2 | 去重 | 计算结果卡片的倍率标签文字不变 | `EffectPill` 内联了 `½/¼`，`multiplierText` 已经在做同样的事 | `multiplierText`（`Team.kt`） | 无 | 档 1 | — | Done |
| S1 | 结构 | 包、公开符号与测试都不变 | 见架构结论第 1 条 | `TypeChartScreen.kt`、`SpeedScreen.kt`、`TeamScreen.kt`、`FavoritesScreen.kt`、`DexViewModel.kt`、`SpriteCache.kt` | 测试与被测代码同包，只移动文件，不需要改测试 | 档 1 + 单元测试 | R1、R2 | Approved |
| X1 | 刻意保留 | `DamageCalc.kt` 逐行移植上游（包括 `checkInfiltrator` 与 `clearScreens` 的重复、读取防守方能力等级等） | 文件头注释；`CalculatorEngineTest` 逐例对照 fixtures | — | fixtures | — | — | Deliberate |
| X2 | 刻意保留 | 计算器请求使用 JSON 字符串 | 与 fixtures 请求格式一致 | — | fixtures、`CalculatorInputTest` | — | — | Deliberate |
| X3 | 刻意保留 | `decodeTeam` 兼容旧的 `team` ID 列表 | 用户本地数据可能仍是旧格式 | `Team.kt` | `TeamTest.storedTeamsKeepSetsAndReadTheOldIdList` | — | — | Deliberate |
| N1 | 否决 | 拆成 `data/`、`calc/`、`ui/` 等多个包 | 要改所有 import 和测试的 package 声明；同包按文件拆分已足够让新人找到代码 | — | 全部测试 | — | — | Rejected：收益低于改动面 |
| N2 | 否决 | 把菜单路由字符串（`"pick:attacker"`、`"set:2"`）换成密封类 | 只在单个文件内使用，可以直接存进 `rememberSaveable`；换成密封类要额外写 Saver | — | — | — | — | Rejected：不划算 |
| N3 | 否决 | `DexViewModel.load` 用 `catch (e: Exception)` 会连带捕获取消异常 | 只在 ViewModel 清除时发生，不会被用户看到 | — | — | — | — | Rejected：没有可观察的影响 |
