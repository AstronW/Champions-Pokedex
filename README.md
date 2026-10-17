# Champions 图鉴

原生 Kotlin / Jetpack Compose Android 图鉴，中文界面，离线运行。

## 功能

- 侧边栏导航：宝可梦、招式、特性、状态说明、属性克制、伤害计算、速度线、队伍构建、收藏与对比；中英文与编号搜索，底部面板筛选与排序。
- Champions 原生伤害计算器：形态、能力点、能力调整、特性、道具、单双打、天气、场地与多段伤害；可从宝可梦详情带入。规则与边界见 [计算器说明](docs/CALCULATOR.md)。
- 全部物种与形态；种族值、体重、性别、普通/隐藏特性、可学招式。
- 已开放招式与全部特性，以及可离线阅读的 52poke 详解（含状态资料）。招式、特性、状态和分类可在应用内互相跳转。
- 52poke 百科分类，保留成员、相关招式/特性与段落提及的区别；招式目标和特殊判定独立展示。状态支持别名检索，Champions 游戏说明与百科历史语境分开。
- 全部原始招式和学习关系保留（数量见 `data/import-report.json`）；未开放招式可从学习页开启显示，已收藏的未开放招式也可找回。
- 普通/异色图片、收藏、亮色/暗色/跟随系统、减少动态效果。
- 惰性网格/列表、后台解析与图片解码、48 MiB 图片缓存、返回栈状态恢复。

## 安装与构建

`dist/ChampionsDex-0.1.0-preview.apk` 是经过 R8 优化的本地体验版，使用本机 Android 调试证书签名，最低 Android 8.0。正式分发应另行配置自己的发布签名；不要提交私钥。

Windows 已安装 Android Studio 时：

```powershell
./build.ps1
# 调试版
./build.ps1 -DebugBuild
# 更新计算器数据与参考用例（需 Node.js、Python 与邻目录 champout）
./build.ps1 -ImportCalculator
```

其他环境需要 JDK 17+、Android SDK Platform 37；设置 `JAVA_HOME`、`ANDROID_HOME` 后执行 `./gradlew :app:assemblePreview`。AGP、Compose 编译插件与 Compose BOM 的版本以根目录与 `app/` 的 `build.gradle.kts` 为准；AGP 使用内置 Kotlin。

```powershell
# 仅向明确指定的设备安装
adb -s <serial> install -r dist/ChampionsDex-0.1.0-preview.apk
```

## 重新导入数据

默认目录结构：本仓库、`champout/`、`sprites/` 位于同一个父目录。

```powershell
python -m pip install -r scripts/requirements.txt
python scripts/import_data.py
# 可显式指定源路径与已生成的输出目录
python scripts/import_data.py --champout ../champout --sprites ../sprites --output ../champout/output/<fingerprint>
./build.ps1 -ImportData
```

导入器读取 champout 最新 manifest，逐项校验游戏源文件、Wiki XML 和导出物哈希。源数据过期时会停止，需要先在 champout 重新 `prepare`。导入不会写入 champout 的源文件、缓存或旧编辑数据库。

Wiki 使用最新导出中的 `expanded`，不读取历史编辑草稿；转换繁简和展示标记，保留效果说明、历史版本语境、表格及来源修订。游戏数值与 Wiki 说明分开，特殊命中值不显示为 101%，动态威力不误显示为固定 1。

分类、特殊判定、招式目标和状态引用从同一 manifest 的 `wiki_*` 文件导入。`data/mechanics-records.json` 保存未裁剪的记录（含章节上下文、模板修订与差异）；`data/mechanics-sources.json` 和 `data/status-sources.json` 保存分类来源与完整状态原文。应用的 `mechanics.json` 使用精简字段，状态正文优先展示已展开的效果节，没有效果节时选“影响对战”或概述。

图片只来自 `sprites/pokemon/versions/generation-ix/champions/` 及其 `shiny/`；属性图标来自 `sprites/types/generation-ix/champions/small/`。缺图的形态明确显示占位，`data/sprite-map.json` 记录逐形态映射与缺失项；彩粉蝶仅匹配经查看确认的花园花纹。PokeAPI 元数据只用于图片 ID 关联，固定版本见 `data/pokeapi/SOURCE.json`。

## 验证

```powershell
python -m unittest discover -s scripts -p test_import.py -v
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
adb -s <test-emulator> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <test-emulator> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <test-emulator> shell am instrument -w dev.champions.dex.test/androidx.test.runner.AndroidJUnitRunner
```

数据清单见 `data/import-report.json`；实际验证范围和限制见 [验证记录](docs/VALIDATION.md)。

## 源码入口

`scripts/import_data.py` 负责数据适配及 Wiki 转换；`Models.kt`、`DexRepository.kt` 提供模型与离线读取，`DexViewModel.kt` 保存设置、收藏与队伍，`SpriteCache.kt` 预解码图片；`MainActivity.kt` 是侧边栏与返回栈；`BrowseScreen.kt` 是查询入口；`DetailScreen.kt` 展示游戏资料；`TypeChartScreen.kt`、`SpeedScreen.kt`、`TeamScreen.kt`、`FavoritesScreen.kt` 分别是属性克制、速度线、队伍构建与收藏对比；`components/` 是按职责分文件的设计系统组件（每个文件附亮色、暗色 Preview，示例数据在 `components/Preview.kt`）；`WikiContent.kt` 把导入生成的百科 HTML 片段（统一存放在 `assets/wiki.json`）原生解析并平铺在详情页（不使用 WebView、不加载远程资源）；`Theme.kt` 定义 Organic 配色与属性色，`TypeChart.kt` 是属性相性表。

## 来源

游戏资料来自本地 champout 提取结果，图片来自 [PokeAPI sprites](https://github.com/PokeAPI/sprites)。百科文字来自 [52poke 神奇宝贝百科](https://wiki.52poke.com/)，各条目保留修订链接与贡献者归属，按 [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/) 共享。完整来源声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

构建参考：[Android 内置 Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)、[Compose BOM](https://developer.android.com/develop/ui/compose/bom)、[AndroidX Test](https://developer.android.com/jetpack/androidx/releases/test)。
