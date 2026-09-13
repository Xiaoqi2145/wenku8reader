# 网格遮挡治理方案（纯色控件遮挡网格）

日期：2026-09-13。承接 `reader-chrome-grid-unification-plan.md`。

## 一、审计结论

对照 GalIsland 模板语义：**网格是"桌面"，卡片是"纸片"**——网格底纹铺满窗口，
卡片（实心 + 细描边 + 8dp 圆角）落在网格上，网格在卡片间隙中可见。
当前偏离有三种形态：

### A. 该有网格却没有网格底纹（整屏纯色）

| 页面 | 现状 |
| --- | --- |
| 书架列表 `fragment_bookshelf_list.xml` | 裸 CoordinatorLayout，网格卡片浮在纯色上（主力浏览页，无网格） |
| 历史记录 `fragment_visit_history.xml` | colorBackground 纯色根 + `gi_panel` 整行实心条 |
| 更多 `fragment_more.xml` | colorBackground 纯色根 |

### B. 有网格但被实心块大面积二次遮挡

| 位置 | 现状 |
| --- | --- |
| 详情页 sw600dp 左栏 | `colorSurfaceContainerLow` 整列（212dp 宽）实心，网格全被盖住 |
| 详情页底部操作条 | `colorSurfaceContainerLow` 实心横条 |
| 排行/分类/书架/首页 AppBar | 整块 `giSurfaceToolbar` 实心（模板 toolbar 本就实心，**保留**，不计入问题） |

### C. 网格之上的实心控件（合理，不动）

- `item_novel_cover_grid` 已透明，仅封面图遮挡，符合模板
- `Widget.GalIsland.CardView` 实心 + 1dp 描边 = 模板 `.library-card`，合理
- 导航轨/底部导航、阅读页 dock、工具栏：实心功能表面，合理

### D. 网格"存在感"不足

- 网格线 `gi_grid_line`=0.5dp、颜色取 `colorOutlineVariant`：浅色主题下过淡，
  卡片稍微密集就看不见网格，视觉上整屏仍像纯色。
  模板参考值：`--border: rgba(39,35,38,0.08)`、1px、40px 单元。

## 二、改进方案

### P0 补网格底纹（A 类，已实施）

| # | 改动 |
| --- | --- |
| 1 | `fragment_bookshelf_list.xml` 根换 `LibrarySurfaceLayout`（colorBackground 底），与推荐/搜索一致 |
| 2 | `fragment_visit_history.xml` 根换 `LibrarySurfaceLayout`；`item_visit_history` 行卡保留 `gi_panel` 纸片样式（模板允许实心卡片，网格在行间透出） |
| 3 | `fragment_more.xml` 内容区接入网格表面（NestedScrollView 根 → 外包 LibrarySurfaceLayout 或根换背景透明 + 宿主网格） |

### P1 削减二次遮挡（B 类，已实施）

| # | 改动 |
| --- | --- |
| 4 | 详情页 sw600dp 左栏去 `colorSurfaceContainerLow`：封面卡与按钮列直接落在网格上（封面本身已是卡片，按钮是控件，均不需要底色托底） |
| 5 | 详情页底部操作条：去 `colorSurfaceContainerLow` 改 `giSurfaceToolbar` 或保留描边分隔的工具条语义（与全局工具条一致） |

### P2 网格可见度（D 类，已实施）

| # | 改动 |
| --- | --- |
| 6 | 新增 `giGridLine` 颜色 attr（对齐模板 border alpha：浅色 rgba(39,35,38,0.08)、深色 rgba(255,255,255,0.08)），网格线宽升 1dp（`gi_stroke`），`LibrarySurfaceLayout` 改读新 token |
| 7 | 网格单元维持 `gi_grid_unit`=40dp 不变 |

### P3 防回归（已实施）

| # | 改动 |
| --- | --- |
| 8 | `check-galisland.ps1` 新增：浏览页（书架/历史/推荐/搜索/详情）根容器必须是 `LibrarySurfaceLayout`；其直接子树禁止新增不透明 `android:background`（卡片/工具栏白名单除外） |

## 三、本轮决策与边界

- 二级页统一接入网格：设置、账户、关于、下载、下载详情、评论、用户书架、书架搜索、全局搜索、颜色选择、登录。
- 历史页行卡保留实心 `gi_panel` 纸片样式；网格在卡片间隙透出，不把卡片强行透明化。
- 详情页底部操作条改为 `giSurfaceToolbar`，保留工具条语义与分隔线。
- 不改动排行/分类/首页 AppBar 的实心工具条（与模板一致）。
- 不让卡片透明化（模板卡片就是实心纸片）。
- 启动页、引导页、崩溃页、图片查看器保持专用纯色/全屏语义，避免网格干扰关键流程。

## 五、当前验收

- `check-galisland.ps1` 通过。
- `assembleDebug` 成功。
- 79 项单测 0 失败。
- Lint 0 errors / 157 warnings / 2 hints；警告均为非阻断项。
- 设备截图矩阵仍需安装权限恢复后进行：书架、历史、更多、详情及全部二级页（手机/平板、浅/深色），重点确认网格在纸片间隙可见、无整片无描边纯色块。
