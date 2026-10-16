# 项目约定

默认简体中文。应用是原生 Kotlin / Jetpack Compose Android 图鉴，完整范围与数据边界见 `docs/PLAN.md`，当前验证证据见 `docs/VALIDATION.md`。

- 游戏数值来自邻目录 `champout`；导入合同由 `scripts/import_data.py` 与 `scripts/test_import.py` 定义。不要修改上游源文件或旧编辑数据库。
- 图片只使用邻目录 `sprites` 中的 Champions 资源；形态映射与缺失见 `data/sprite-map.json`。全国图鉴编号不等于所有形态的图片 ID。
- 不手改生成的 `app/src/main/assets/dex.json` 或 Wiki HTML；修改导入规则后重新生成。
- Wiki 说明与游戏数值分开。来源哈希、修订链接、特殊数值和未开放招式都必须保留。
- 页面状态按返回栈条目保存；不得在重新进入组合时无条件重置列表。亮暗主题和横屏均须保留。
- 以 `./build.ps1` 输出优化的本地体验版；正式发布密钥不进入仓库。
- 设备验证必须指定 `adb -s <serial>`；不要对所有已连接设备批量安装或重启。
- 验证与变化相称，构建不等于真实页面、设备性能验收。
- 界面设计以 `Theme.kt` 与 `components/` 的 Organic 设计系统为准，新增或修改组件时同步维护该文件里的 `@DexPreviews`；使用 `material-3` 技能时先读 `.claude/skills/material-3/PROJECT.md`，Material 3 只作交互、无障碍与布局原则参考。
