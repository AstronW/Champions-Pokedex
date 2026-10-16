---
name: champions-dex-workflow
description: Champions 图鉴（PokeDex）项目的改动—生成—构建—验证流程。凡是在本仓库修改导入规则、数据、计算器资源、Kotlin/Compose 代码，或需要构建 APK、跑单元测试/lint/设备交互测试、启动模拟器检查真实页面、截图、写 docs/VALIDATION.md 记录时都使用本技能；即使用户只说“改一下××页面”“重新导入”“打个包”“验证一下”“装到模拟器看看”也应使用。
---

# Champions 图鉴工作流

本项目是离线的 Kotlin / Jetpack Compose 图鉴。数据从邻目录 `champout`、`sprites`、`damage-calc` 生成，产物带来源哈希，构建脚本会核对。大部分出错都源于“手改了生成物”或“验证对象不清楚”，下面的流程就是为了避免这两类问题。

开始前先读 `AGENTS.md`；涉及范围和数据边界时读 `docs/PLAN.md`，涉及计算器时读 `docs/CALCULATOR.md`。所有说明、提交信息与文档默认使用简体中文。

## 1. 先判断改动类型

| 改动 | 正确的修改位置 | 重新生成 / 检查 |
|---|---|---|
| 图鉴数据、Wiki 转换、图片映射 | `scripts/import_data.py`（合同在 `scripts/test_import.py`）、`data/` 中的人工规则 | `./build.ps1 -ImportData`（同时刷新计算器资源，需要 Python、Node） |
| 计算器数据导出、形态映射 | `scripts/calculator/build.cjs`、`scripts/calculator/form-aliases.json` | `./build.ps1 -ImportCalculator`（同时刷新参考用例） |
| 计算规则、输入校验与结果格式 | `DamageCalc.kt`（上游 Champions 规则移植）、`CalculatorEngine.kt` | Kotlin 单元测试逐例对照 `scripts/calculator/fixtures.json` |
| 界面与应用逻辑 | `app/src/main/java/dev/champions/dex/*.kt` | Gradle 单元测试、lint、相关的设备交互测试 |
| 只改文档 | `docs/`、`README.md` | 无需构建 |

以下生成物不能手改，改了也会被下一次导入覆盖，或者被 `build.ps1` 的哈希检查拦下：`app/src/main/assets/dex.json`、`wiki.json`、`mechanics.json`、`assets/calculator/*`（含 `provenance.json`）、`scripts/calculator/fixtures.json`、`data/bundle-sha256.json`、`data/import-report.json`。要改结果，就去改生成它的规则，然后重新生成。

上游目录（`../champout`、`../sprites`、`../damage-calc`）和旧编辑数据库只读。源数据过期时导入器会停止，这时请用户在 champout 里重新 `prepare`，不要绕过校验。

数据边界会直接影响界面文案，必须保留：游戏数值与 Wiki 说明分开展示；保留来源修订号与哈希；特殊命中不显示成 101%；动态威力不显示成固定 1；未开放招式默认隐藏但仍可找到；缺图要明确显示占位，不能用别的形态顶替。

## 2. 构建与测试命令（PowerShell，在仓库根目录执行）

```powershell
# 数据合同
python -m unittest discover -s scripts -p test_import.py -v
# Kotlin 单元测试（含原生计算与上游参考用例逐例对照）、lint、设备测试 APK
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
# 优化体验版 → dist/ChampionsDex-0.1.0-preview.apk 及 .sha256
./build.ps1
# 调试版
./build.ps1 -DebugBuild
```

- 没设置 `JAVA_HOME` 时，`build.ps1` 会使用 Android Studio 自带的 `jbr`；直接调用 Gradle 时要自己设置 `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'`。
- 如果 `build.ps1` 报“计算器资源与数据版本不匹配”，说明图鉴数据变了而计算器资源没跟上。运行 `./build.ps1 -ImportCalculator`，不要手改 `provenance.json`。
- lint 目前有约 10 条历史提示。判断标准是“0 错误、无新增”，需要和上一次记录比较。
- 发布签名密钥不进仓库，体验版使用本机 debug 证书。

## 3. 设备验证

`AGENTS.md` 规定：所有 adb 命令都要带 `-s <serial>`，不对所有已连接设备批量安装或重启。用户可能同时连着真机或其他模拟器，所以默认单独启动一个只读模拟器：

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
# 用后台方式运行（run_in_background），-read-only 不改动 AVD 本身
& "$sdk\emulator\emulator.exe" -avd Pixel_10 -port 5580 -read-only -no-snapshot-save
adb -s emulator-5580 wait-for-device
adb -s emulator-5580 shell getprop sys.boot_completed   # 返回 1 后再继续
```

交互测试的运行方式：先安装两个 APK，再用 `am instrument`。不要直接用 `connectedAndroidTest`，它会装到所有设备上；确实要用时，先设置 `$env:ANDROID_SERIAL='emulator-5580'`。

```powershell
adb -s emulator-5580 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5580 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# 全量
adb -s emulator-5580 shell am instrument -w dev.champions.dex.test/androidx.test.runner.AndroidJUnitRunner
# 只跑一个类（与改动相称时优先）
adb -s emulator-5580 shell am instrument -w -e class dev.champions.dex.NavigationFlowTest dev.champions.dex.test/androidx.test.runner.AndroidJUnitRunner
```

真实页面检查（安装 `dist/` 中的 Preview APK）：

```powershell
adb -s emulator-5580 install -r dist/ChampionsDex-0.1.0-preview.apk
adb -s emulator-5580 shell am start -W -n dev.champions.dex/.MainActivity
adb -s emulator-5580 shell cmd uimode night yes          # 暗色；no 恢复
adb -s emulator-5580 shell settings put system accelerometer_rotation 0
adb -s emulator-5580 shell settings put system user_rotation 1   # 横屏；0 恢复竖屏
# 截图：PowerShell 的 > 重定向会破坏二进制，所以先存到设备上再 pull
adb -s emulator-5580 shell screencap -p /sdcard/shot.png
adb -s emulator-5580 pull /sdcard/shot.png screenshots/<主题>-preview-<页面>.png
adb -s emulator-5580 logcat -b crash -d                  # 应为空
adb -s emulator-5580 emu kill                            # 结束时关闭本次模拟器
```

每张截图都要实际打开查看再下结论。亮色、暗色、横屏和返回栈状态（进入详情再返回，列表位置和展开状态不变）是本项目的固定检查项。

已知的环境噪声，不要误判成应用问题：
- 全新启动的模拟器可能弹出 System UI 无响应对话框，抢走焦点导致首轮测试失败。点“等待”后复测。
- 输入法大约 0.8 秒后才弹出，会挤压菜单。测试辅助函数在输入后调用 `Espresso.closeSoftKeyboard()`，再滚动到目标。
- SwiftShader 软件渲染下帧时间本身偏高，所以模拟器上的帧率数字不能当成真机性能结论。

## 4. 验证要与改动相称

- 只改文档：不构建。
- 只改界面局部：单元测试 + lint + 相关测试类 + 对应页面的亮色、暗色、横屏截图。
- 改导航、状态保存或公共组件：全量交互测试。
- 改导入或计算器：数据合同测试、计算器对照，并重新生成 APK，核对哈希。

构建成功不等于页面正确，模拟器通过也不等于真机性能达标。没验证过的路径（例如 Android 8–9、真机）在记录里写明“未验证”。

## 5. 写验证记录

在 `docs/VALIDATION.md` 最上方插入新的一节，格式与已有记录一致：

```markdown
## YYYY-MM-DD 本次改动标题

体验版：`dist/ChampionsDex-0.1.0-preview.apk`，N 字节。SHA-256：`…`。（数据、Wiki、图片与计算内核是否改动）

- 改动要点……

|检查|结果|
|---|---|
|Kotlin 单元测试|N 项通过|
|Android 交互|全量/定向 N 项通过（`emulator-5580`）；失败及原因如实写|
|Android lint|0 错误；N 条原有提示，无新增|
|Preview 实际页面|检查了哪些页面与主题，截图 `screenshots/…`|

设备范围（只操作了哪个 serial）、crash buffer 情况、未验证的路径与限制。

---
```

失败、重试和测试修正都要写出来，不能只写结果。用户明确要求之前不提交 Git；用户要求提交时，提交信息用中文、使用 `feat:` / `fix:` / `chore:` 前缀。
