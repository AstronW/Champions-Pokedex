# 来源

- 上游：https://github.com/android/skills（`system/edge-to-edge/`，Google LLC）
- 固定提交：`42dc2270e96032bd860bb94511e440aa00a43125`（2026-10-09 下载）
- 许可：Apache-2.0，见同目录 `LICENSE.txt`
- 未修改上游文件。本项目在 `MainActivity` 中调用的是 `ComponentActivity.enableEdgeToEdge()`，系统栏图标颜色由它自动处理，所以不要再按技能里 `WindowCompat` 一节的说明手动设置 `isAppearanceLight*`。
