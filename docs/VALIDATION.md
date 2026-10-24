# 验证记录

最近的验证在本文件，新记录插在最上面。2026-10-09 及更早的记录（含最初交付时的数据核对）移到了 [VALIDATION-HISTORY.md](VALIDATION-HISTORY.md)，其中的数量、路径反映的是当时的状态。

## 2026-10-24 统一招式列表

未放入 `dist/`，验证用本地构建的体验版 APK（12,460,020 字节，SHA-256 `c1299f6e0d2ac2cd2f1ed386afa714d930447b281c30882f632d16e0a144398e`）。图鉴数据、Wiki、图片与计算资源未改动。

- 新增 `MoveList.kt`：招式目录、宝可梦详情的可学招式、伤害计算招式菜单、队伍招式选择共用 `MoveFilterState`（含搜索词，按返回栈条目保存）、`MoveSearchBar`（搜索框＋筛选按钮＋已选条件）、`MoveFilterSheet` 和 `moveItems`（`MoveRow` 行＋吸顶表头）。各页只配置招式范围、是否显示命中、额外条件。
- 招式目录、可学招式、计算器、队伍选择的表头滚动时固定在顶部。
- 队伍构建的招式栏改为 `PickerField`（从 `SelectField` 拆出，可带前置图标），显示所选招式的属性图标；点开后是 `MovePickerSheet`，可搜索、按分类/属性筛选、排序，可清除当前招式。
- 计算器招式菜单的分类、属性、本系、效果绝佳从页内控件改为筛选面板（分类只有全部/物理/特殊），当前招式高亮，不在学习列表中的招式标“假设”。
- 可学招式页去掉单独的“筛选 · 排序”按钮，改用同一搜索栏，新增搜索。

|检查|结果|
|---|---|
|Kotlin 单元测试|通过|
|Android 交互|编译通过，未在设备上运行（见下）|
|Android lint|新增的 `ModifierParameter` 已修复；剩余 1 项 `AndroidGradlePluginVersion`（Gradle 9.8.2 可用），与本次改动无关|
|实际页面（体验版）|亮色竖屏：招式目录滚动后表头固定；队伍招式栏显示属性图标，选择面板表头固定、当前招式高亮，筛选“特殊＋草”后 7 / 54 个并显示条件标签，取消后队伍未改动；计算器招式菜单表头固定、当前招式高亮。暗色竖屏：计算器招式菜单|

`emulator-5580` 因已有非只读模拟器运行而无法启动，改为用 `adb -s emulator-5554` 在已运行的模拟器上覆盖安装同签名体验版（保留数据），主题已恢复为亮色；crash buffer 中没有本应用记录。未验证：横屏、大字体、减少动态效果、交互测试（debug 签名与已装应用不同，需卸载才能装）。

---

## 2026-10-19 详情页动图

未打体验版，验证用 debug APK。图鉴数据、Wiki、内置图片与计算资源未改动。

- 详情页头图改为从 PokeAPI/sprites 固定提交 `ba403973` 的 `generation-ix/champions` 下载 512px 动画 WebP（文件 ID 与内置 PNG 一致，353 个形态及异色全部存在）。先加载当前形态与配色，显示后在不计流量网络上依次预取其他形态与异色。
- 加载中先显示内置 PNG，图片区右上角显示小转圈，动图解码完成后淡入替换（减少动态效果时直接切换）；离线、下载失败或 Android 8.x 保留内置 PNG。缓存在 `cacheDir/animated-sprites/<提交>`，超过 256MB 按最近使用淘汰，换提交时清掉旧目录；下载先写 `.part`，校验 WebP 文件头后再改名。
- 设置页新增“缓存”卡片：显示宝可梦动图缓存占用，可一键清除（只删动图）。
- 新增 `INTERNET`、`ACCESS_NETWORK_STATE` 权限。

|检查|结果|
|---|---|
|Kotlin 单元测试|通过|
|Android lint|`No issues found.`|
|实际页面（debug）|暗色竖屏：喷火龙详情加载中显示占位（后改为显示内置 PNG，改后仅编译与 lint，未再上设备）、约数秒后显示清晰动图，前后两帧截图不同；当前图显示后缓存目录出现 3 个形态 × 2 种配色共 6 个文件；切换异色立即显示（来自缓存）；关闭网络并清空缓存后打开水箭龟，显示内置 PNG|

仅操作以 `-read-only` 启动的 `emulator-5580`；crash buffer 中没有本应用记录，模拟器已关闭。未验证：亮色、横屏、计流量网络下不预取、Android 8.x 回退、真机内存与流畅度。

---

## 2026-10-17 动画与交互响应

体验版：`dist/ChampionsDex-0.1.0-preview.apk`，12,443,564 字节。SHA-256：`783c7944fd0d5ce91ef84efbc6ddca5609f750784c055dd17c4d3f0d3d47cb3e`。图鉴数据、Wiki、图片与计算资源未改动。

- 导航抽屉改为自有 `DexModalDrawer`（`components/Drawer.kt`）：遮罩从开始滑入起即可点按关闭（Material 1.4 的遮罩要等动画完全结束才响应）；开合用强调缓动 tween 代替长尾弹簧；减少动态效果时开合都是瞬时的。
- 底部面板 `DexSheet` 不再用 Material `ModalBottomSheet`（每次打开都新建 dialog 窗口），改为同一窗口内的 `SheetHost` 图层，点按后的下一帧就开始出现；减少动态效果时无滑动；返回键、遮罩、下拉都可关闭；筛选内容在横屏下可滚动。
- 从抽屉切换列表页：旧页直接移除、不再交叉淡入淡出，新页首帧画完后，页面淡入上移与抽屉收起同时开始。动效常量放在 `Theme.kt`（`EnterEasing`/`ExitEasing`/`ENTER_MS`/`EXIT_MS`）。
- 伤害计算场地菜单：单侧状态改为攻击方、防守方左右两栏同时显示。属性详情在“攻击时”和“防守时”之间加分隔线。
- 新增交互测试：抽屉滑入途中点遮罩可关闭；减少动态效果时抽屉与筛选面板不滑动。

|检查|结果|
|---|---|
|Kotlin 单元测试|7 个类、29 项通过|
|Android 交互|全量 10 项通过（`emulator-5580`）；新增减少动态效果用例第一次断言写错（文字自带 26dp 内边距），改为与动画结束位置比较后通过|
|Android lint|`No issues found.`|
|实际页面（体验版）|暗色竖屏：筛选面板、场地菜单、属性详情分隔线；点遮罩关闭抽屉，下方列表没有被误触。亮色横屏：场地菜单两栏、筛选面板（修复前底部按钮被截断，加滚动后可达）|

仅操作以 `-read-only` 启动的 `emulator-5580`，adb 全部指定 serial；Pixel Launcher 反复弹出无响应（已知模拟器噪声），手动点按测试因此改由交互测试的手动时钟验证；crash buffer 中没有本应用记录；旋转已恢复，模拟器已关闭。软件渲染下无法评估流畅度，卡顿是否消除需真机确认；暗色横屏、大字体未验证。

---

## 2026-10-17 全面重构第二轮

体验版：`dist/ChampionsDex-0.1.0-preview.apk`，12,540,204 字节。SHA-256：`a866baf47092089f59817be7fa26a72ee8652e27305993d1f09f7cfda80755ab`。图鉴数据、Wiki、图片与计算资源未改动；构建前核对 `provenance.json` 中的 `dexSha256`、`catalogSha256`、`formAliasesSha256`，一致。

- 第二轮代码审计修复 D3：在超级进化形态详情页点“加入队伍”会提示“第 0 位”，并且可以重复加入；改为按实战形态查找（`Dex.teamSlotOf`），并加锁定测试。R3、R4 为小清理。
- 测试补深度（T1）：`koText`、`filterMoves`、`withForm`/`normalized`、`multiplierText`、`natureFor` 新增 6 项，8 处变异全部能被测出。
- 2026-10-09 及更早的验证记录移到 `VALIDATION-HISTORY.md`。

|检查|结果|
|---|---|
|Kotlin 单元测试|7 个类、29 项通过|
|Android 交互|全量 6 项通过（`emulator-5580`），一次通过|
|Android lint|`No issues found.`|
|实际页面（体验版）|亮色竖屏：伤害计算场地菜单。亮色横屏 + 字体缩放 1.3：同一菜单在旋转后保持打开，8 个单侧状态开关末行左对齐。超级喷火龙 X 详情：第一次点“加入队伍”提示“已加入队伍第 1 位”，第二次提示“超级喷火龙 X 已在队伍第 1 位”|

仅操作以 `-read-only` 启动的 `emulator-5580`，adb 全部指定 serial；crash buffer 中没有本应用记录；字体缩放与自动旋转已恢复，模拟器已关闭。暗色横屏、减少动态效果、真机、Android 8–9 未验证。

---

## 2026-10-17 全面重构第一轮（审计台账 `docs/REFACTOR-AUDIT.md`）

未打包体验版，检查使用 debug APK。图鉴数据、Wiki、图片与计算资源未改动。

- 修复：计算器去掉上游 Champions 规则从不读取的蓄电池、能量点、钢之意志、花之礼 4 个开关（D1），新增锁定测试，要求页面提供的每个开关都被引擎读取；Wiki 页面解析失败不再写入缓存（D2）。
- 重构：JSON 读取辅助函数合并到 `Json.kt`（R1）；`EffectPill` 改用 `multiplierText`（R2）；`ToolsScreen.kt` 拆为 4 个页面文件，`DexRepository.kt` 拆出 `DexViewModel.kt`、`SpriteCache.kt`（S1）。包与符号不变，原有测试未改动。
- 新增特征测试 `MechanicsParseTest`（C1），两处变异均能让它失败。
- 文档：更正 README、CALCULATOR、PLAN、material-3/PROJECT.md 中与代码不符的说明（F1–F10）。

|检查|结果|
|---|---|
|Kotlin 单元测试|7 个类、22 项通过（原 17 项 + C1 的 4 项 + D1 的 1 项）|
|Android 交互|全量 6 项通过（`emulator-5580`），一次通过|
|Android lint|`No issues found.`|
|实际页面|暗色竖屏：抽屉、伤害计算页（倍率标签“效果绝佳 ×2”）、场地菜单（单侧状态 8 项，末行左对齐）|

仅操作本次以 `-read-only` 启动的 `emulator-5580`，adb 全部指定 serial；crash buffer 中没有本应用记录，模拟器已关闭。亮色、横屏、大字体、真机未验证；体验版 APK 未重新打包。

---

## 2026-10-16 清理 Android lint 提示

体验版：`dist/ChampionsDex-0.1.0-preview.apk`，12,540,204 字节。SHA-256：`99cdf2ab849d047be5e726bf118f5215904d0cd77ae0efd8220569c8e0d58d31`。图鉴数据、Wiki、图片与计算内核未改动（构建前按 `provenance.json` 核对计算器资源哈希，一致）。

- `ConfigurationScreenWidthHeight`：详情页与伤害计算页的 `compact` 判断改用 `LocalWindowInfo.current.containerSize` 换算 dp，阈值仍为 500dp。
- `ModifierParameter`：`Glyph` 的 `modifier` 默认值改为 `Modifier`，22dp 默认尺寸移到组件内部（`modifier.size(22.dp)`，调用方给出的尺寸优先）；`Kicker` 的 `modifier` 移到第一个可选参数，5 处按位置传颜色的调用改为 `color =` 具名参数。
- `AutoboxingStateCreation`：状态详情页签改用 `mutableIntStateOf`。
- 版本提示：Gradle 9.6.0 → 9.8.1，`compose-bom` 2026.08.00 → 2026.09.00，测试用 `org.json` 20240303 → 20260814。

|检查|结果|
|---|---|
|Kotlin 单元测试|17 项通过|
|Android 交互|全量 6 项通过（`emulator-5580`），一次通过|
|Android lint|0 错误、0 警告（原 8 条警告、1 条提示全部清除）|
|Preview 实际页面|横屏亮色、暗色：喷火龙详情、由详情进入的伤害计算页（紧凑布局生效，结果卡位于右侧栏）；返回、交换图标尺寸正常。截图 `screenshots/{light,dark}-preview-{detail,calculator}-landscape-lint.png`|

仅操作本次以 `-read-only` 启动的 `emulator-5580`（AVD `Medium_Phone_API_37.0`），adb 全部指定 serial。第一次实例关闭前 crash buffer 有 38 行，但关闭前没有查看内容；随后重启的同一只读实例在安装应用前，crash buffer 同样是 38 行，内容全部是 `com.google.android.bluetooth` 的原生崩溃（HCI 硬件错误），判断为模拟器蓝牙噪声，但不能百分之百排除本应用。复查时第二次实例在安装 APK 时因宿主 GPU 层（gfxstream）崩溃退出；第三次实例（`-gpu swiftshader_indirect`）在启动阶段被前一次遗留的后台命令执行 `emu kill` 关闭，没有完成复查。系统暗色与旋转设置已恢复，模拟器均已关闭。本机没有 PowerShell，以 `sh ./gradlew :app:assemblePreview` 构建后复制到 `dist/` 并写入 `.sha256`。竖屏下的这两页、真机、Android 8–9 未验证。没有提交 Git。

---

## 2026-10-16 导航迁移到 Navigation 3

体验版：`dist/ChampionsDex-0.1.0-preview.apk`，12,540,204 字节。SHA-256：`30be7a49b58fb2a03e99c14b1f724c580f7ce98ea706b985d918dcf30a4901b4`。图鉴数据、Wiki、图片与计算内核未改动（构建前按 `provenance.json` 核对计算器资源哈希，一致）。

- 依赖：新增 `androidx.navigation3:navigation3-runtime` 与 `navigation3-ui` 1.2.0（稳定版）。路由仍是字符串，不需要 Kotlin 序列化插件；R8 后体验版增加 33,157 字节。
- 详情返回栈交给 `NavDisplay`：栈底固定 `HomeKey`（内含原有的顶层页 `AnimatedContent` 与每页 `SaveableStateHolder`），其上是 `DetailKey(route)`。返回栈本身仍以 `rememberSaveable` 保存路由列表，`contentKey` 就是路由。
- 删除手写的 `detailStates`、条目自增序号与 `isReturningToDetail`：每个条目的保存状态由 `SaveableStateHolderNavEntryDecorator` 管理，出栈后自动清除；前进/返回方向由 Navigation 3 比较前后返回栈判断。`openDetailEntry` 的去重截断保留，改为直接按路由比较。
- 过渡：详情页之间沿用原来的横向淡入滑动；从目录进入或返回目录时，目录保持不动（`EnterTransition.None` / `ExitTransition.KeepUntilTransitionsFinished`），与迁移前“详情页叠在目录上”的观感一致。新增预测性返回手势动画，使用同一过渡。
- 返回键：栈内返回由 `NavDisplay` 处理；抽屉开着时先关抽屉；栈空且不在图鉴页时先回图鉴页。目录页不再常驻在详情页下方的组合里，返回时重新组合可见项，滚动与筛选状态由保存状态恢复。
- Android Studio 内置 lint 在分析局部扩展函数 `isHome` 时抛 `ClassCastException`（`JoinEffectDetector`，IDE 缺陷），已将两个过渡函数改为文件顶层私有函数。

|检查|结果|
|---|---|
|Kotlin 单元测试|17 项通过（`DetailNavigationTest` 改为按路由断言）|
|Android 交互|全量 6 项通过（`emulator-5580`），一次通过；含 `NavigationFlowTest` 的重建恢复与入场动画中返回|
|Android lint|0 错误；9 条均为原有提示，无新增|
|Preview 实际页面|暗色：图鉴列表滚动后进入怪力详情再返回，位置不变；横屏下打开详情并返回，列表正常。亮色：抽屉、招式列表滚动后进入日光束详情再返回，切到宝可梦页再切回招式，滚动位置仍保留。截图 `screenshots/nav3/`|

仅操作本次以 `-read-only` 启动的 `emulator-5580`（AVD `Medium_Phone_API_37.0`），结束后已关闭；crash buffer 为空。第一次启动模拟器失败：Android Studio 当时正以非只读方式运行同一 AVD（`emulator-5554`）。本机没有 PowerShell，以 `sh ./gradlew :app:assemblePreview` 构建后复制到 `dist/` 并写入 `.sha256`。预测性返回手势（边缘滑动）、返回目录首帧的真机帧耗时（SwiftShader 下 gfxinfo 数字不作结论）、Android 8–9 与真机未验证。

---

## 2026-10-16 拆分通用组件并添加 Preview

体验版：`dist/ChampionsDex-0.1.0-preview.apk`，12,507,047 字节。SHA-256：`f1f3e2f2bfc72b77a02d32f3d245dd30e71c1ef9d59838bf47e8bfe6092c9e1e`。图鉴数据、Wiki、图片与计算内核未改动。

- 741 行的 `Components.kt` 按职责拆到新包 `dev.champions.dex.components`：`Icons`、`Images`、`Labels`、`TypeTags`、`Controls`、`Surfaces`、`Feedback`、`Header`、`Search`、`Menus`、`Sheets`、`SelectField`、`Rows`。组件代码原样搬移，各页面只增加一行 `import dev.champions.dex.components.*`。
- 底部面板拆出无状态内容：`FilterSheetContent`、`PokemonPickerContent`/`PokemonPickerRow`、`SelectOptionsContent`。搜索词等状态仍由原来的外层组件持有并保存，行为不变；拆出来的内容组件不依赖 `ModalBottomSheet`，可以直接预览。
- 新增 `components/Preview.kt`：`@DexPreviews`（360dp 宽，亮色与暗色）、`@LargeTextPreview`（字体缩放 1.3）、`PreviewSurface`，以及取自 `dex.json` 的示例数据（含动态威力、必中、未开放招式和缺图形态）。每个组件文件末尾都有私有 Preview，共 21 个。
- `rememberAsset` 在 `LocalInspectionMode` 下改为同步软件解码，使静态 Preview 能显示精灵图；应用内仍走 `SpriteCache` 的异步硬件位图路径。
- 依赖：新增 `implementation("androidx.compose.ui:ui-tooling-preview")`。R8 后体验版增加 193 字节，只有 Preview 用到的字符串（如 `shiny_10034.png`、`Mega Charizard X`）已不在 `classes.dex` 中。

|检查|结果|
|---|---|
|Kotlin 单元测试|17 项通过|
|Android 交互|全量 6 项通过（`emulator-5580`），一次通过|
|Android lint|0 错误；9 条均为原有提示，无新增（2 条 `ModifierParameter` 随代码从 `Components.kt` 移到 `components/Icons.kt`、`Labels.kt`）|
|Preview 渲染|debug 包内用 ui-tooling 的 `PreviewActivity` 在模拟器上逐个启动，亮色与暗色各看了 `PokemonRowPreview`、`MoveRowPreview`、`TypeTagsPreview`、`FilterSheetPreview`、`MenuPanelPreview`、`ImagesPreview`，均正常|
|Preview 实际页面|体验版图鉴列表 → 筛选面板（应用外观为暗色），显示与交互正常|

仅操作本次以 `-read-only` 启动的 `emulator-5580`（AVD `Medium_Phone_API_37.0`），结束后已关闭；crash buffer 为空。本机没有 Android Studio，IDE 内的静态 Preview（即 `LocalInspectionMode` 下的同步解码分支）未验证；本机没有 PowerShell，以 `sh ./gradlew :app:assemblePreview` 构建后复制到 `dist/` 并写入 `.sha256`。其余 15 个 Preview、横屏与真机未验证。

---

## 2026-10-16 删减测试

APK 未变（测试代码不进 APK，与下一节同一个体验版）。只保留守住真实逻辑与项目硬性规则的测试：计算结果与上游逐例一致、页面状态按返回栈条目保存并经历重建与旋转、特殊数值不当普通值显示、旧队伍数据可读、Wiki 正文不丢字。删去复述数据、只断言文字存在、与其他测试重复的用例。

- 单元测试 39 → 17：删除 `CalculatorCatalogTest`、`MechanicsTest` 整个文件；`CalculatorEngineTest` 去掉重复计算与默认计算 2 项；`CalculatorInputTest` 只留百变怪无招式、切换形态保留配置、交换两侧 3 项；`DetailNavigationTest` 留切换形态不累积返回步与重访 2 项；`ModelsTest` 留属性筛选、搜索、特殊数值、属性相性 4 项；`TeamTest` 留超级进化映射、旧队伍解码、特性道具受伤倍率、特性改变招式属性 4 项；`WikiParserTest` 留表格解析与全部条目不丢字 2 项。
- 设备交互测试 26 → 6，耗时约 79 秒 → 20 秒：删除 `MechanicsFlowTest`、`TeamFlowTest` 整个文件；`CalculatorFlowTest` 留天气结果经导航、重建、横屏保持与非法能力点不留旧结果 2 项；`DexFlowTest` 留搜索—详情—招式—返回与列表滚动位置保持 2 项；`NavigationFlowTest` 留形态切换返回栈与入场动画中返回 2 项。被删的两项会把应用外观改成“暗色”，上次记录里外观残留的问题随之消失。
- `CalculatorFlowTest` 删去不再使用的 `choose`、`pick`、`title` 与对应导入；技能文档中单类运行的示例改为 `NavigationFlowTest`。

|检查|结果|
|---|---|
|Kotlin 单元测试|17 项通过|
|Android 交互|全量 6 项通过（`emulator-5554`），一次通过|

仅操作 `emulator-5554`，crash buffer 为空。

---

## 2026-10-16 暗色配色与统一属性组件

体验版：`dist/ChampionsDex-0.1.0-preview.apk`，12,506,854 字节。SHA-256：`3bfeb89a8eb2eb762afd0a91bd865facfb8e627f6c18e8d3a6d3faca2e02a78d`。图鉴数据、Wiki、图片与计算内核未改动。

- 暗色卡片底色：列表行、详情页主图、队伍卡等用的 `typeTone().blob` 由 `oklch(.33, c×.5)` 改为 `oklch(.31, min(c×.18, .018))`。低明度下色度过高会让火、格斗变棕，虫、电、地面变橄榄色，现在只留一点属性色相，接近中性。
- 暗色属性颜色：属性图标与胶囊底色同用一个颜色矩阵（饱和度 ×0.6、亮度 ×0.76），两者仍然连成一体，不再刺眼；文字颜色按变暗后的底色重新判断（白字对比度不低于 3:1 时用白色）。亮色主题不变。
- 统一属性组件：所有显示属性的地方都用 `TypeTag`，颜色一律取图标底色。参数 `showName` 控制是否显示文字（关闭时只显示图标，读屏仍报属性名），`expanded` 控制是否撑满可用宽度，`trailing` 用于在文字后加内容（如筛选条件的关闭符号）。`TypeIcon` 改为私有；去掉 `solid`、`onTint`、`iconColor` 三种旧配色。详情页属性、招式详情、筛选条件、属性选择网格与计算器属性条、属性克制分组、队伍构建、队伍分享图、招式行图标均改用它；属性克制表在 Canvas 中绘制的图标也同样变暗。属性选择不再用反色表示选中，而是在外围加一圈描边，属性保持自己的颜色。
- 验证中发现并修复：不撑满的标签放进横向滚动行时，文字因 `weight` 分到 0 宽度而消失（筛选条件只剩图标和 ×）。现在只有 `expanded` 时文字才用 `weight`。

|检查|结果|
|---|---|
|Kotlin 单元测试|39 项通过（删减测试前）|
|Android 交互|全量 26 项通过（`emulator-5554`，删减测试前），一次通过|
|Android lint|0 错误；9 条均为原有提示，无新增|
|构建哈希|按 `build.ps1` 规则用 Python 核对 provenance 中 28 个文件哈希，全部一致（本机无 PowerShell，以 `sh ./gradlew :app:assemblePreview` 构建后复制到 `dist/` 并写 `.sha256`）|
|Preview 实际页面|暗色：图鉴列表竖屏与横屏、妙蛙花详情、筛选面板（选中火属性）、筛选条件胶囊、招式详情与招式行；亮色：图鉴列表、妙蛙花详情。列表与详情的属性颜色逐像素一致（草 `#45713b`）。截图 `docs/screenshots/{dark,light}-preview-{list,detail}-types.png`、`dark-preview-filter-types.png`、`dark-preview-list-types-landscape.png`|

仅操作用户指定的 `emulator-5554`，adb 全部指定 serial，没有清除应用数据；crash buffer 为空。应用内外观原为“暗色”，拍亮色截图时临时改为“亮色”，之后已改回；系统暗色与自动旋转设置已恢复原值，筛选条件已清除。计算器属性条、队伍分享图、属性克制表、大字体与真机未逐一截图验证。

---

## 2026-10-16 图鉴列表超级进化标记与属性胶囊配色

体验版：`dist/ChampionsDex-0.1.0-preview.apk`，12,506,854 字节（上次 12,486,384）。SHA-256：`84e84357dff6869c6db7f10faa93118bb798f340c7fa2b0cc257902dbd3698a5`。图鉴数据、Wiki、宝可梦图片与计算内核未改动；新增资源 `assets/Mega_Evolution_symbol.png`（3,946 字节）。

- 图鉴列表：有超级进化形态的物种不再显示“可超级进化”文字胶囊，改为编号旁的超级进化标志（高 18 dp，按原图 56:74 比例，无障碍描述仍为“可超级进化”）；“N 种形态”胶囊不变。该图标加入启动预解码，滚动时不闪烁。`PokemonRow` 新增 `mega` 参数。
- 属性胶囊：`TypeTag` / `TypeTagRow` 新增 `iconColor` 模式，背景取属性图标自身的底色（`Theme.kt` 中的 `TypeIconColors`，从 `assets/types/*.png` 取样，18 种均为纯色），图标与胶囊连成一体。文字在白色对比度不低于 3:1 时用白色，与图标白色图形一致，否则用亮色主题墨色：一般、格斗、飞行、岩石、虫、钢、电、冰、妖精为墨色，其余为白色。两种主题使用同一组颜色。目前只有图鉴列表启用；详情页、伤害计算与队伍分享图仍用原配色。

|检查|结果|
|---|---|
|Kotlin 单元测试|39 项通过|
|Android 交互|全量 26 项通过（`emulator-5554`），一次通过|
|Android lint|0 错误；9 条均为原有提示，无新增|
|构建哈希|按 `build.ps1` 规则用 Python 核对 provenance 中 28 个文件哈希，全部一致（本机无 PowerShell，以 `sh ./gradlew :app:assemblePreview` 构建后复制到 `dist/` 并写 `.sha256`）|
|Preview 实际页面|图鉴列表亮色竖屏、暗色竖屏、亮色横屏，以及电属性（电龙，浅底墨色文字）；超级进化标志、胶囊底色与图标一致，横屏两列布局正常。截图 `docs/screenshots/{light,dark}-preview-list-mega.png`、`light-preview-list-mega-landscape.png`、`light-preview-list-electric.png`|

仅操作用户指定的 `emulator-5554`（Medium_Phone_API_37.0，用户原有的模拟器，非只读实例），adb 全部指定 serial，没有清除应用数据；crash buffer 为空。交互测试结束后应用外观残留为“暗色”，首轮亮色截图因此作废，在抽屉里改回“跟随系统”后重拍；系统暗色与旋转设置已恢复原值，搜索框已清空。构建期间曾误给 `gradlew` 加执行权限，已还原。数据合同测试（`test_import.py`）需要 champout，未运行，导入脚本未改动。真机、Android 8–9、字体缩放下的列表未验证。工作区原有的 AGP 9.4.1 / Gradle 9.6.0 未提交改动保留并参与本次构建。没有提交 Git。

---

## 2026-10-16 伤害计算改为 Kotlin 原生实现

体验版：`dist/ChampionsDex-0.1.0-preview.apk`，12,486,384 字节（上次 13,006,578）。SHA-256：`84b83c58c0649cd1cfb0a0f75eac2aababb86c347c105b5d08e899c2777a5bb7`。图鉴数据、Wiki、图片未改动；计算内核由 Rhino 解释的 JavaScript 改为 Kotlin 移植，`catalog.json` 增加上游导出的机制数据。

- 新增 `DamageCalc.kt`：逐行移植上游固定提交的 Champions 规则（`mechanics/champions.ts`、`mechanics/util.ts` 及相关类），用 `Double` 复现 JavaScript 算术与就地修改顺序；照搬上游怪癖（速度上限 999、亲子爱子代为 0 时按无子代处理等）。`CalculatorEngine.kt` 取代 `adapter.js`，校验文案与结果格式不变，结果改为类型化的 `CalcDamage` / `CalcFailure`。
- APK 移除 `engine.js`、`adapter.js`、Rhino 依赖、Rhino 许可证与 ProGuard 规则；启动不再预热内核。计算在组合中同步完成，去掉 60 ms 防抖与“正在准备计算内核”；首次打开前只解析约 110 KB 的 `catalog.json`，列表显示后在 IO 线程预解析。
- `scripts/calculator/build.cjs` 改为经 `upstream.cjs` 在 Node 中加载上游源码，只导出数据（招式机制字段、道具效果、属性相性）。本机没有邻目录 champout，导入按新规则在图鉴哈希与上次导入一致时复用已校验的道具中文名；其余 `catalog.json` 字段与旧版逐项相同，仅 `items` 增加效果字段。
- `test.cjs` 以上游源码加原 `adapter.js` 生成 1238 个参考用例：38 个命名场景（新增亲子爱固定伤害、挣扎、速度上限 999）、1188 个固定种子组合、12 个校验错误。

|检查|结果|
|---|---|
|上游覆盖检查|`node scripts/calculator/test.cjs` 通过：396 形态、335 攻击招式，命名用例与直接调用上游 API 一致|
|Kotlin 单元测试|39 项通过；`CalculatorEngineTest` 1238 个用例逐字段（伤害、逐段随机值、百分比、阈值概率、实数值、段数、错误文案）一致|
|额外压力对照（未提交）|另一种子、30 轮共 11,929 个用例，强制重跑全部一致|
|变异检查|分别把生命宝珠 5324 改 5325、`pokeRound` 的 `>` 改 `>=`、速度上限 999 改 10000，对照测试均失败；最后一项起初未被覆盖，补充“Speed cap 999”用例后可检出|
|构建哈希|按 `build.ps1` 规则用 Python 核对 provenance 中 28 个文件哈希，全部一致（本机无 PowerShell，未直接运行 `build.ps1`）|
|Android 交互|全量 26 项通过（`emulator-5580`），一次通过|
|Android lint|0 错误；9 条均为原有提示（上次 10 条，少的是 Rhino 相关依赖提示），无新增|
|Preview 实际页面|暗色与亮色竖屏伤害计算、伤害分布、改为晴天后结果即时更新、横屏、许可页及返回后状态保留；默认组合 108–128 HP 与晴天 162–192 HP / 12.5% 和 Node 中上游结果一致。截图 `docs/screenshots/*-preview-calculator-*native.png`|

仅操作本次独立启动的只读 `emulator-5580`（Medium_Phone_API_37.0），adb 全部指定 serial；crash buffer 为空。交互测试后应用残留暗色外观设置，清除应用数据后再拍亮色截图；结束时恢复竖屏并关闭模拟器。Linux 上以 `sh ./gradlew` 构建，`gradlew` 无执行权限未修改。真机性能、Android 8–9 未验证。数据合同测试（`test_import.py`）需要 champout，未运行，导入脚本未改动。工作区原有的 AGP 9.4.1 / Gradle 9.6.0 未提交改动保留并参与本次构建。没有提交 Git。

---

## 2026-10-15 属性克制横屏并排、大字体标题栏

体验版：`dist/ChampionsDex-0.1.0-preview.apk`，13,006,578 字节。SHA-256：`4636b8be4d2b2b373c0e15f33ba21444b0ed3fd9421533799ab0ffb7c3bf19a3`。数据、Wiki、图片与计算内核未改动。

- 属性克制：窗口宽于高且宽度 ≥ 600 dp（手机横屏、平板横屏）时，左侧为表格、图例与说明，右侧为攻防组合与属性详情，各自滚动；未选择时右侧提示如何操作。竖屏布局不变。上次记录的“横屏结果需要滚动才能看到”已解决。
- 标题栏（`AppHeader`）由固定 56 dp 改为至少 56 dp，并加上下 4 dp 内边距。字体缩放 1.3 时，带小标题的页面（速度线、图鉴、伤害计算等）原先标题顶部被裁切，现在标题栏随文字增高；字体缩放 1 时内容不足 56 dp，高度不变。
- 速度线：实数值与“快于 N 只，同速 M 只”分两行，字体缩放 1.3 时不再把数字和排名挤在一起（原先显示成“14517 只”）。

|检查|结果|
|---|---|
|Kotlin 单元测试|36 项通过|
|Android 交互|全量 26 项通过（`emulator-5580`，两次：属性克制改动后、标题栏改动后）；新增 `DexFlowTest.typeChartCellTogglesItsMatchupAndClears`（点击格子显示组合、再次点击取消、“清除选择”）|
|Android lint|0 错误；10 条均为原有提示，无新增|
|Preview 实际页面|横屏亮色属性克制未选择与已选择、横屏暗色已选择，旋转回竖屏后选择保留；字体缩放 1.3 下的竖屏属性克制、图鉴列表、速度线、伤害计算；开启减少动态效果后速度线“定位”直接跳到目标行。截图 `screenshots/uiux-preview-types-landscape-*.png`、`screenshots/uiux-preview-*-font130.png`、`screenshots/uiux-preview-speed-locate-reduced.png`|

仅操作本次独立启动的只读 `emulator-5580`（Pixel_10 / API 36 / SwiftShader），adb 全部指定 serial；crash buffer 中没有本应用记录。结束前恢复字体缩放 1、竖屏、亮色并清除应用数据，随后关闭模拟器。平板与折叠屏的并排布局未实测。没有提交 Git。
