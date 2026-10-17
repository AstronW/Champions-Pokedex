---
name: champions-dex-workflow
description: Champions 图鉴（PokeDex）项目的改动—生成—构建—验证操作手册。在本仓库修改导入规则、数据、计算器、Kotlin/Compose 代码，或需要构建 APK、跑测试、上模拟器看页面、截图、写 docs/VALIDATION.md 时使用；用户只说“改一下××页面”“重新导入”“打个包”“验证一下”“装到模拟器看看”也适用。
---

# Champions 图鉴工作流

规则以 `AGENTS.md` 为准，这里只写怎么做。涉及数据边界读 `docs/PLAN.md`，涉及计算器读 `docs/CALCULATOR.md`。

## 验证分级：默认最轻

先按改动选档，**只做该档要求的事**；更重的检查等用户要求，或改动确实落在下一档时再做。

| 档 | 典型改动 | 要做的 |
|---|---|---|
| 0 | 文档、注释、技能说明 | 不构建 |
| 1（多数改动） | 界面样式、文案、单个页面或组件、格式化、重构但不改行为 | `sh ./gradlew :app:compileDebugKotlin`；改了组件就顺手更新对应 `@DexPreviews` |
| 2 | 计算、模型、Wiki 解析、导航逻辑等有单元测试覆盖的代码 | 档 1 + `sh ./gradlew :app:testDebugUnitTest` |
| 3 | 导航/状态保存/返回栈、公共组件行为、依赖升级、导入或计算器数据 | 档 2 + lint + 设备交互测试 + 相关页面亮暗、横屏截图，并写 `docs/VALIDATION.md` |

- 档 1、2 不需要启动模拟器、不截图、不写验证记录，汇报时说明做了哪档、什么没验证即可。
- lint 判断标准：0 错误、无新增提示。
- 打包体验版只在用户要“打个包”或档 3 时做。

## 生成物与重新生成

不能手改（会被覆盖或被哈希检查拦下）：`app/src/main/assets/dex.json`、`wiki.json`、`mechanics.json`、`assets/calculator/*`（含 `provenance.json`）、`scripts/calculator/fixtures.json`、`data/bundle-sha256.json`、`data/import-report.json`。

| 改动 | 修改位置 | 重新生成 |
|---|---|---|
| 图鉴数据、Wiki 转换、图片映射 | `scripts/import_data.py`（合同 `scripts/test_import.py`）、`data/` | `python3 scripts/import_data.py && python3 -m unittest discover -s scripts -p test_import.py`，然后执行下一行 |
| 计算器数据、形态映射 | `scripts/calculator/build.cjs`、`form-aliases.json` | `npm ci --prefix scripts/calculator --ignore-scripts --no-audit --no-fund && node scripts/calculator/build.cjs && node scripts/calculator/test.cjs` |
| 计算规则 | `DamageCalc.kt`、`CalculatorEngine.kt` | 单元测试对照 `scripts/calculator/fixtures.json` |

导入器报源数据过期时，请用户在 `../champout` 里重新 `prepare`，不要绕过校验。

## 构建

本机是 Linux、没有 PowerShell，`build.ps1` 跑不了，按它的逻辑手动执行：

```bash
sh ./gradlew :app:assemblePreview --console=plain
cp app/build/outputs/apk/preview/app-preview.apk dist/ChampionsDex-0.1.0-preview.apk
(cd dist && sha256sum ChampionsDex-0.1.0-preview.apk > ChampionsDex-0.1.0-preview.apk.sha256)
```

数据或计算器改过时，打包前核对 `provenance.json` 里记录的哈希（`dexSha256`、`catalogSha256`、`formAliasesSha256`、`sources`），不一致就重新导入计算器，不要手改。Windows 上直接用 `./build.ps1`（`-ImportData`、`-ImportCalculator`、`-DebugBuild`）。

## 设备验证（仅档 3 或用户要求）

SDK 在 `~/Android/Sdk`，`adb`、`emulator` 不在 PATH 里；AVD 为 `Medium_Phone_API_37.0`。单独起一个只读实例，避免碰到用户的其他设备：

```bash
SDK=~/Android/Sdk; ADB="$SDK/platform-tools/adb -s emulator-5580"
"$SDK/emulator/emulator" -avd Medium_Phone_API_37.0 -port 5580 -read-only -no-snapshot-save   # 后台运行
$ADB wait-for-device; $ADB shell getprop sys.boot_completed   # 等到 1
```

交互测试：先装两个 APK 再 `am instrument`（不要用 `connectedAndroidTest`，它会装到所有设备上）。能只跑相关类就只跑相关类：

```bash
sh ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
$ADB shell am instrument -w [-e class dev.champions.dex.NavigationFlowTest] dev.champions.dex.test/androidx.test.runner.AndroidJUnitRunner
```

看真实页面：

```bash
$ADB install -r dist/ChampionsDex-0.1.0-preview.apk
$ADB shell am start -W -n dev.champions.dex/.MainActivity
$ADB shell cmd uimode night yes                     # 暗色；no 恢复
$ADB shell settings put system accelerometer_rotation 0
$ADB shell settings put system user_rotation 1      # 横屏；0 恢复
$ADB exec-out screencap -p > screenshots/<主题>-<页面>.png
$ADB logcat -b crash -d                             # 应为空
$ADB emu kill
```

截图要实际打开看过再下结论。已知噪声：新模拟器可能弹 System UI 无响应（点等待后复测）；crash buffer 里 `com.google.android.bluetooth` 的崩溃是模拟器蓝牙噪声；软件渲染下的帧率不能当真机性能结论。

## 验证记录（仅档 3）

在 `docs/VALIDATION.md` 顶部插入一节，沿用已有格式：日期与标题、APK 大小与 SHA-256、改动要点、检查表（单元测试 / 交互 / lint / 实际页面）、设备范围与未验证项。失败与重试如实写。
