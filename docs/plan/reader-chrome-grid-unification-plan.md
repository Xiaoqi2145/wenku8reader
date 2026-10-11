# 网格风统一 + 平板适配 + 阅读页底栏缺陷修复方案

日期：2026-09-13。依据：平板（2506×1673，横屏）截图五张 —— 详情页、首页、沉浸阅读、菜单呼出阅读、异常浮空阅读。

## 一、截图问题的根因诊断

### A. 底栏浮空（图 5：正文与状态 HUD 之间一条带虚线的空白灰带）

布局结构（`activity_vertical_read.xml` / `activity_horizontal_read.xml` 相同）：

```
read_bottom_dock (LinearLayout, background=bg_read_bottom_bar)   ← 带容器色背景 + 1dp 顶描边
├── view_read_bottom_bar  (include, 又重复设置了 bg_read_bottom_bar)
└── view_read_status_bar  (include, 常显 HUD)
```

`ReaderChromeController` 的 `hide()` 只对**内层** `bar_a_*_read.root` 做 `translationY` 平移
（两个 ReadActivity 均如此传入，horizontal/ReadActivity.kt:269、vertical 同样）。
`translationY` 不改变布局占位：底栏内容移走了，dock 仍以完整高度留在屏幕上，
其背景色与 1dp 顶描边（高分屏缩略后呈"虚线"）继续绘制 —— 即截图中的浮空白带。

附加重叠：dock 与内层 bar 双重设置同一背景。

### B. 菜单底色异常占用（图 4：菜单下方多出一大截粉色区域）

1. `ReaderChromeController.applySystemBars()` 把 `navigationBarColor` 刷成
   `colorSurfaceContainer`。平板横屏手势导航区较高，系统把这块区域整片刷成菜单色，
   视觉上像菜单"溢出"占据了双倍高度。
2. 菜单显示时常显状态行（HUD）仍显示在菜单正下方，且 HUD 自带 1dp 顶描边，
   菜单面板与屏幕底边之间多出一截同色带 + 一条多余分界线。

### C. 网格风与平板适配缺口

- 网格列表已覆盖：推荐 / 分类 / 排行 / 完结 / 搜索 / 书架（`LibrarySurfaceLayout` +
  `NovelGridLayoutManager` + `item_novel_cover_grid`，列数按实际宽度 152dp 单元自适应）。
- 未统一：详情页（无网格底纹、目录卷卡未对齐网格基线）、历史记录（线性 item）、
  下载列表、评论区等仍是非网格表面。
- 阅读页底栏在平板横屏全宽拉伸，进度滑块过长，未限宽居中。

## 二、修复方案

### P0 阅读页 chrome 重构（根因修复，两模式共用）

| # | 改动 | 说明 |
| --- | --- | --- |
| 1 | 布局拆分 | 两个 `activity_*_read.xml`：状态行 `view_read_status_bar` 移出 dock，作为 root（CoordinatorLayout）的直接 bottom 子 View 常显；dock 只含底栏。背景唯一来源：dock 保留 `bg_read_bottom_bar`，删掉内层 bar 的重复背景。 |
| 2 | 控制器修正 | `ReaderChromeController` 构造改收整个 dock；`hideTranslation` 以 root 高度计算，把整个 dock（含背景）完全移出屏幕底缘。隐藏态不再残留任何 dock 像素。 |
| 3 | 菜单/HUD 互斥 | 菜单显示时状态行淡出（或 GONE），隐藏时恢复；消除菜单下方的多余色带与双描边。 |
| 4 | 导航区几何 | 迁移到 edge-to-edge：`setDecorFitsSystemWindows(false)`，dock 用 `WindowInsets.navigationBars` 加 bottom padding，菜单表面连续延伸到手势区（色带即菜单本体，而非异常溢出）；沉浸态 dock 移出后导航区回刷阅读背景色，与正文一体。 `ReaderWindowInsetsCoordinator` 改为真正消费/分发 inset。 |
| 5 | 平板限宽 | dock 内容在宽屏居中并设最大宽度（参考目录抽屉 `min(屏宽×0.86, 420dp)` 的思路，底栏内容限 ~840dp），滑块不再横跨整个横屏。 |

涉及文件：`activity_vertical_read.xml`、`activity_horizontal_read.xml`、
`view_read_bottom_bar.xml`（去重复背景）、`ReaderChromeController.kt`、
`ReaderWindowInsetsCoordinator.kt`、`ReaderChromeGeometry.kt`、
两个 `ReadActivity.kt`（传入 dock、状态行互斥逻辑）。

### P1 网格风统一（GalIsland tokens 不变：40dp 网格单元、8dp 卡片、细描边）

| # | 页面 | 改动 |
| --- | --- | --- |
| 6 | 详情页（手机 + sw600dp 双栏） | 根容器接入 `LibrarySurfaceLayout` 网格底纹；目录卷卡片对齐网格基线（内边距取 `gi_space_*`）。 |
| 7 | 历史记录 / 下载列表 | 表面接入网格底纹；线性 item 卡片化（8dp 圆角、outlineVariant 描边、surface 底）。 |
| 8 | 评论区 / 回复 | 统一表面与卡片样式。 |
| 9 | 存量梳理 | 排查 `item_novel_cover_liner` 剩余使用处，统一到网格卡片或 GalIsland 列表卡。 |

### P2 验证

| # | 项 |
| --- | --- |
| 10 | `scripts/check-galisland.ps1` 新增结构断言：状态行不在 dock 内、底栏背景唯一、dock 引用正确 id。 |
| 11 | `ReaderChromeGeometryTest` 补用例：dock 整体隐藏位移、inset  consumed 后几何、宽屏限宽。 |
| 12 | 全量验证：`.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug '-Pksp.incremental=false' --no-daemon`。 |

## 三、待确认的取舍

1. 菜单显示时状态行：**隐藏（推荐）** 还是保留？——隐藏可彻底消除图 4 的多余色带。
2. 导航区策略：**edge-to-edge 菜单表面延伸（推荐）**，还是保持系统导航区与菜单分色？
3. P1 的 7/8/9（历史、下载、评论卡片化）是否本轮一并做，还是只做阅读页 P0 + 详情页？

## 四、验收矩阵（沿用既有口径）

| 页面 | 浅色/深色 | 手机 | 平板 sw600dp 横屏 |
| --- | --- | --- | --- |
| 阅读页菜单呼出/隐藏 | 无浮空带、无溢出色块 | 待截图 | 待截图 |
| 首页/书架/详情网格 | 网格底纹、列数自适应 | 待截图 | 待截图 |
