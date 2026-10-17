# 本项目对 material-3 技能的覆盖说明

本项目的视觉来自 Claude Design 稿“宝可梦图鉴应用设计”v2 的 Organic 设计系统，不是 Material 3 默认样式。使用 material-3 技能时，按下表决定以谁为准。

| 方面 | 以本项目为准 | 仍可参考 Material 3 |
|---|---|---|
| 颜色 | `Theme.kt` 中的 `pal`（`bg`、`surface`、`text`、`accent`、`accent2`、`n()`、`a()`、`g()` 色阶）和 `typeTone(type)`。不启用动态取色，不在页面中硬编码 `Color(0x…)` | 配色成对使用（容器色 + 对应前景色）、文字对比度 4.5:1、`divider` 只作装饰 |
| 字体 | 标题用 Caprasimo（`HeadingFont`，只用 `HeadingWeight`，不加粗），正文用 Figtree（`BodyFont`），中文回落到系统字体；`Theme.kt` 的 `MaterialTheme.typography` 定义了标题、正文、标签各级样式。新组件优先使用这些样式；已有页面里直接写的 `fontSize` 可以保留，修改时沿用所在页面的写法，不必为此专门迁移。不改用 Roboto | 字号层级的用途划分、使用 sp 并适配系统字体缩放 |
| 形状 | 控件用胶囊形 `PillShape`，卡片用 `CardShape`（32dp），列表行 24dp，二级菜单顶部 28dp，`DexSheet` 顶部 40dp | 同类元素保持一致的圆角 |
| 组件 | 优先复用 `components/`（包 `dev.champions.dex.components`）的组件：`DexCard`、`PrimaryButton`、`SecondaryButton`、`GhostButton`、`PillSegment`、`ToggleTile`、`Pill`、`TypeTag`、`AppHeader`、`SearchField`、`MenuHost`/`MenuPanel`、`DexSheet`（画在根部 `SheetHost` 图层，不开 dialog 窗口）、`DexModalDrawer`、`SelectField`、`RoundIconButton`、`Glyph`、`EmptyResult`。不要为了“符合 MD3”把它们换成 Material 默认组件 | 组件选择原则（例如几个选项用分段控件、多选用开关）和各组件的交互状态 |
| 导航 | 左上角菜单打开侧边栏（自有 `DexModalDrawer`：遮罩滑入途中即可点按关闭，减少动态效果时瞬时开合），详情页由 Navigation 3 的 `NavDisplay` 管理返回栈（`DetailNavigation.kt`），每个条目的状态随条目保存、出栈后清除；顶层页用 `SaveableStateProvider` 各自保存。不改成底部导航栏或 Navigation Compose | 横屏和大屏的布局思路 |
| 动效 | 先读取 `LocalReduceMotion`，开启“减少动态效果”时时长为 0。页面从右侧滑入，菜单从底部滑入，时长约 220–280ms；新写的动效用 `Theme.kt` 的 `EnterEasing`/`ENTER_MS`（300ms）和 `ExitEasing`/`EXIT_MS`（200ms）。顶层页切换不交叉淡入淡出，新页首帧画完再开始动画 | 进入用减速曲线、退出用加速曲线 |
| 布局 | 横屏判断沿用 `LocalWindowInfo.current.containerSize` 换算的高度 < 500dp（compact）。计算器结果在竖屏时位于底部，横屏时位于右侧 | 窗口尺寸断点（600 / 840dp）、宽屏限制内容最大宽度、间距取 4dp 的倍数 |
| 无障碍 | 中文 `contentDescription`，设置 `Role` 和 `onClickLabel`；测试用的 `testTag` 保持稳定 | 触控目标至少 48dp（本项目图标按钮为 44dp，新增控件建议用 `heightIn(min = 48.dp)`）、不只用颜色传达信息 |

## 用 material-3 的审查（audit）模式时

审查本项目时，“没有使用 Material 默认组件”“没有动态取色”“没有使用 Roboto”都不算问题。只有偏离了上表左列（Organic 设计系统本身）时，才记为不合规。

## 本项目固定的界面检查

每次改界面后都要检查：亮色和暗色、竖屏和横屏、进入详情再返回时状态保持、开启减少动态效果，以及大字体（字体缩放 1.3）。只有队伍分享图片例外，它固定按 380dp 宽、字体缩放 1 排版。设备上的检查流程见 `champions-dex-workflow` 技能。
