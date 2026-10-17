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
| S1 | 结构 | 包、公开符号与测试都不变 | 见架构结论第 1 条 | `TypeChartScreen.kt`、`SpeedScreen.kt`、`TeamScreen.kt`、`FavoritesScreen.kt`、`DexViewModel.kt`、`SpriteCache.kt` | 测试与被测代码同包，只移动文件，不需要改测试 | 档 1 + 单元测试 | R1、R2 | Done |
| D3 | 缺陷（第二轮） | 在超级进化形态的详情页点“加入队伍”：提示显示“第 0 位”，同一超级形态可以重复加入 | 队伍保存为“原形态 + 超级石”，`DetailScreen` 却按超级形态 ID 比较 | `Dex.teamSlotOf`（`Team.kt`），按 `battleForm` 比较 | 无 | 档 2 + 锁定测试 `aMegaFormIsFoundInTheTeamThroughItsHolderAndStone` | — | Done |
| R3 | 去重（第二轮） | 设置页的“可用招式”数量不变 | `SettingsScreen` 自己重算 `moves.count { it.available }`，与 `Dex.availableMoves` 重复 | `Dex.availableMoves` | 无 | 档 1 | — | Done |
| R4 | 清理（第二轮） | 引用列表行为不变 | `MechanicsScreen` 两处 `items` 的解构变量 `refs`、`rows` 没有用到 | — | 无 | 档 1 | — | Done |
| X1 | 刻意保留 | `DamageCalc.kt` 逐行移植上游（包括 `checkInfiltrator` 与 `clearScreens` 的重复、读取防守方能力等级等） | 文件头注释；`CalculatorEngineTest` 逐例对照 fixtures | — | fixtures | — | — | Deliberate |
| X2 | 刻意保留 | 计算器请求使用 JSON 字符串 | 与 fixtures 请求格式一致 | — | fixtures、`CalculatorInputTest` | — | — | Deliberate |
| X3 | 刻意保留 | `decodeTeam` 兼容旧的 `team` ID 列表 | 用户本地数据可能仍是旧格式 | `Team.kt` | `TeamTest.storedTeamsKeepSetsAndReadTheOldIdList` | — | — | Deliberate |
| N1 | 否决 | 拆成 `data/`、`calc/`、`ui/` 等多个包 | 要改所有 import 和测试的 package 声明；同包按文件拆分已足够让新人找到代码 | — | 全部测试 | — | — | Rejected：收益低于改动面 |
| N2 | 否决 | 把菜单路由字符串（`"pick:attacker"`、`"set:2"`）换成密封类 | 只在单个文件内使用，可以直接存进 `rememberSaveable`；换成密封类要额外写 Saver | — | — | — | — | Rejected：不划算 |
| N3 | 否决 | `DexViewModel.load` 用 `catch (e: Exception)` 会连带捕获取消异常 | 只在 ViewModel 清除时发生，不会被用户看到 | — | — | — | — | Rejected：没有可观察的影响 |

## 文档台账

源码冻结。范围：`AGENTS.md`、`README.md`、`docs/*.md`、`.claude/skills/champions-dex-workflow/SKILL.md`、`.claude/skills/material-3/PROJECT.md`、`THIRD_PARTY_NOTICES.md`。`material-3`、`edge-to-edge` 的其余文件是带 `SOURCE.md` 的上游原样副本，不在范围内。

| ID | 类别 | 问题 | 核对方式 | 状态 |
|---|---|---|---|---|
| F1 | 错误 | README 写目录名为 `PokeDex/` | 仓库目录名 `Champions-Pokedex` | Done |
| F2 | 错误 | README 写图片缓存 24 MiB | `SpriteCache.kt` 中 `48 * 1024 * 1024` | Done |
| F3 | 错误 | README 写 AGP 9.2.1、BOM 2026.08.00 | `build.gradle.kts` 中为 9.4.1、`app/build.gradle.kts` 中为 2026.09.00；改为指向构建文件，不再抄写版本号 | Done |
| F4 | 错误 | README、PLAN 的侧边栏列表漏了“状态说明” | `DexRoot` 枚举 | Done |
| F5 | 错误 / 写死的计数 | README 写 856 篇百科，实际 1022；其余计数改数据时会悄悄过期 | `data/import-report.json` 的 `wikiPages`；去掉计数，改为指向数据文件 | Done |
| F6 | 错误 | CALCULATOR.md 描述的“四张卡片 / 更多条件 / 滑杆”界面已经不存在 | `CalculatorScreen.kt` 的 `SideSummary`、`FieldSummary`、`MoveSummary`、`StepButton` | Done |
| F7 | 错误 | CALCULATOR.md 写可调“队友增益” | 代码 D1 修复后已不提供；`champions.ts` 不读取 | Done |
| F8 | 错误（指令文件） | PROJECT.md 写详情页“自有返回栈” | `MainActivity.kt` 使用 `NavDisplay` | Done |
| F9 | 错误（指令文件） | PROJECT.md 写 compact 判断用 `LocalConfiguration.screenHeightDp` | `CalculatorScreen.kt`、`DetailScreen.kt` 使用 `LocalWindowInfo` | Done |
| F10 | 错误（指令文件） | PROJECT.md 写动效 250–280ms | 详情 220、顶层页 260、菜单 280 | Done |
| V1 | 刻意保留 | `docs/VALIDATION.md` 是按日期记录的历史，旧小节里的路径和数量反映的是当时的状态 | 只在顶部追加新的一节 | Deliberate |
| V2 | 删除 | 66 个截图与 txt 全都被引用（按花括号展开后核对；之前说“大部分没被引用”有误），引用都在 `docs/VALIDATION.md` 的历史小节里。按用户决定，删除设计稿 v2 与计算器原生化之前的 A–E 组共 25 个文件（约 3.4 MB），对应 9 处引用标注“已删除，用 `git show 2ac182e:<路径>` 查看”；F–I 组 39 个文件保留 | `git grep` 按文件名查找，并人工核对带花括号的写法 | Done |

## 测试台账

production 冻结。基线（`7d3be0e`）：单元测试 7 个类、24 项，约 0.7 s；交互测试 3 个类、6 项，全部用条件等待（`waitUntil`），没有写死的 `sleep`。

| ID | 类别 | 内容 | 证据 | 状态 |
|---|---|---|---|---|
| T1 | 补深度 | 关键纯函数此前没有测试：`koText`、`filterMoves` 的排序与未开放过滤、`Dex.withForm`、`Dex.normalized`、`multiplierText`、`CalculatorCatalog.natureFor`。新增 6 项，单元测试共 30 项 | 8 处变异全部能被测出（威力排序的变异第一次没被测出，补了用例后才测出） | Done |
| T2 | 可读性 | `ModelsTest`、`CalculatorInputTest` 的 fixture 写得很挤（不加空格） | 只是格式，改了会掩盖 blame 历史，收益低 | Rejected：仅格式 |
| T3 | 补深度 | 队伍构建（加入成员、配置、分享）没有交互测试 | `grep -n "@Test" app/src/androidTest` 中没有涉及队伍的用例 | Proposed：需要模拟器，单独做 |
| T4 | 刻意保留 | `CalculatorEngineTest` 逐一对照 1238 个参考用例，是单元测试里最慢的一项（约 0.46 s） | 它是移植正确性的唯一证据 | Deliberate |
