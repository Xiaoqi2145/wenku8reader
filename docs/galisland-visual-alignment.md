# GalIsland 原生视觉对齐

参考：`E:\Tool\Galisland-UI-Template` 的 tokens、components、layout 与预览图。

## 已实现

- 明暗主题的 sidebar / toolbar / input / panel / hover 表面角色；8dp 卡片、7dp 控件、细描边、粉紫强调色。
- 手机底部导航和 sw600dp 导航轨使用同一状态色；浏览列表加入轻量网格背景。
- 4:5 网格封面、左对齐双行标题、右上角选择角标与强调描边。
- 列数按 RecyclerView 的实际宽度计算，自动扣除导航轨、内边距并适应窗口尺寸变化。
- 手机详情的信息分层、固定底部继续阅读按钮，以及平板双栏详情；保留原业务回调。
- 本轮补充阅读页 bottom dock 与 WindowInsets 统一契约：底栏隐藏位移只使用父容器坐标，状态 HUD 与底栏同一 dock，避免系统导航区造成浮空间隙；目录抽屉按当前窗口宽度计算。
- 搜索、分类、历史、设置、账户、登录、下载、评论与状态页面统一组件。
- 阅读器控制栏、目录和设置面板统一样式；正文阅读配色与阅读核心不变。竖向顶栏通过绘制顺序覆盖正文，不依靠高程。
- 下载页进入时应用当前主题，补齐相关繁体文案。
- 全量布局维度资源化，新增 `scripts/check-galisland.ps1` 检查 XML、旧组件引用、内联颜色/间距与导航一致性。

## 验证入口

```powershell
.\scripts\check-galisland.ps1
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug '-Pksp.incremental=false' --no-daemon
```

本次遇到 KSP 增量生成缺失 RxHttp 类，验证命令关闭增量生成后编译恢复；无需修改网络业务代码。离线缺少 JUnit/Hamcrest 时已通过联网解析补齐。

本地 SDK 路径的冒号转义已修正；`local.properties` 不纳入版本控制。没有扩充 lint baseline 来隐藏新增错误。

2026-09-10 本次结果：APK 构建成功；71 项单元测试通过；Lint 0 errors、149 warnings、2 hints（另有 282 项被既有 baseline 过滤）；视觉资源扫描通过。

2026-09-13 本轮结果：新增 reader chrome 几何与 WindowInsets 协调器、手机网格列数策略测试；bottom dock 已统一状态 HUD/底栏层级，修复跨坐标系隐藏位移；目录按当前窗口 bounds 计算。完整 Gradle 日志位于 `build/galisland-validation.log`，Lint 报告位于 `app/build/reports/lint-results-debug.html`。

2026-09-13 第二轮（方案见 `docs/reader-chrome-grid-unification-plan.md`）：

- 修复平板底栏浮空：状态行移出 `read_bottom_dock` 独立常显；dock 只承载菜单并独占背景，隐藏时整体平移出屏幕底缘，不再残留浮空带；删除 dock 与内层 bar 的重复背景。
- 修复菜单底色异常占用：阅读页迁移 edge-to-edge，dock/状态行用 inset padding 延伸表面至手势区；菜单呼出时状态行淡出互斥。
- 平板适配：底栏内容限宽 `gi_reader_dock_max_width`(840dp) 居中；详情页（含 sw600dp 双栏）接入 `LibrarySurfaceLayout` 网格表面，内容滚动层去除不透明底色让网格透出。
- `check-galisland.ps1` 修复 UTF-8 读取编码（GBK 解码导致合法 XML 误判）。
- 验证：`check-galisland.ps1` 通过；`assembleDebug` 成功；79 项单测 0 失败；Lint 0 errors / 149 warnings（与基线一致）。

2026-09-13 第三轮：

- 全部浏览页与二级页统一接入 `LibrarySurfaceLayout` 网格表面：书架、历史、更多、设置、账户、关于、下载、下载详情、评论、用户书架、书架搜索、全局搜索、颜色选择、登录。
- 详情平板左栏移除整列 `colorSurfaceContainerLow`，底部操作条改用 `giSurfaceToolbar`；历史行卡保留实心卡片作为网格上的“纸片”。
- 网格线改用主题化 `giGridLine` token，宽度对齐模板 1px/40px（Android 为 1dp/40dp）。
- `check-galisland.ps1` 增加浏览页/二级页网格根容器断言；所有检查、构建与单测通过。Lint 0 errors / 157 warnings / 2 hints。

2026-09-18（审查见 `docs/galisland-visual-drift-audit.md`，修复记录见 `docs/galisland-visual-drift-fix.md`）：

- 修复小说封面比例：`gi_cover_height` 由 160dp(7:10) 改为 140dp(4:5)，列表卡片与目录封面统一；登录页 logo 高度抽出 `gi_brand_logo_height` 避免连带影响。
- 目录封面移除 3dp elevation，全项目非 0 elevation 清零；封面卡片的圆角/描边/高程全部回归样式层，`gi_catalog_cover_elevation` 删除。
- `gi_card_stroke` 由死 token 接入 `Widget.GalIsland.NovelCards.Card`，选中/聚焦态显示主色描边，兑现「强调描边」。
- 消除跨语义域 token 复用：新增 `gi_space_gap`(6) / `gi_space_gap_lg`(14) / `gi_space_gap_xl`(20) / `gi_space_gap_xxl`(40) 与 `gi_thumbnail_width/height`、`gi_brand_logo_height`；`gi_control_icon` 当间距的 **49 处**、`gi_status_icon` 5 处、`gi_grid_unit` 1 处、`gi_radius_tag` 4 处已替换（数值保持不变）。
- styles 层硬编码尺寸全部回填 token：新增 `gi_text_micro`(10sp) / `gi_text_empty_title`(16sp) 与 Slider/Tab/导航指示器几何 token；`galisland_styles.xml` 除 `0dp` 外已无字面量尺寸。
- 新增 `Widget.GalIsland.Switch` 并注册 `materialSwitchStyle`，覆盖原先 10 处未映射的 M3 默认开关（只对齐触控目标 48dp，不缩小开关本体）。
- 删除 `values-night/themes.xml`——与 `values` 版逐项等价的重复定义，Base 主题收敛为单一定义；同步更新 `check-galisland.ps1` 的主题 token 断言（原断言会因该文件缺失而失败）。
- 阅读设置面板（横/竖）重写为与主视觉二级页同一分组语言：`gi_secondary_panel` 分组容器、`TextAppearance.GalIsland.SectionTitle` 标题、`gi_secondary_row_height`(56dp) 行高、行内 padding；统一颜色按钮 id 为 `..._change_color`；补滑块单位注释（横竖排单位不同，勿统一取值范围）。
- 阅读页工具栏改用 `Widget.GalIsland.Secondary.Toolbar`，删除布局中样式已声明的 `background` / `minHeight` / `elevation`。
- 验证：`check-galisland.ps1` 全部断言通过（含 Python 等价复现）；`assembleDebug` BUILD SUCCESSFUL（4m11s，JDK 21）。**构建需显式设置 `JAVA_HOME=D:/SDK/Java/jdk-21`**——系统默认 `java` 为 Zulu JDK 25，Gradle 8.13 不支持。
- 未修：`gi_metric_*` 伪语义 token（26 个 / 约 110 处），理由与推进顺序见 fix 文档。
- 像素观感、触控回归仍待设备验收（安装限制未变）。

2026-09-18 收束修复：

- 阅读器新增共享 `ReaderChromeGeometry.contentInsets`，横排 `PageView` 与竖排 `NestedScrollView` 按实际 Toolbar、bottom dock、状态行边界动态保留内容安全区，控制栏呼出时正文不再被覆盖。
- `ReaderChromeController` 在显隐、WindowInsets 和布局变化时回调内容边界更新；新增 3 项几何单测，单测总数 82 项全部通过。
- `gi_metric_*` 伪语义 token 全量按用途迁移到 `dimens.xml` 的语义 token，旧资源文件删除，静态检查增加回流断言。
- `check-galisland.ps1` 通过；`assembleDebug` 成功；`testDebugUnitTest` 82/82 通过；Lint 0 errors（165 warnings、2 hints，与基线口径一致）。
- 本轮设备安装回归因 ADB 设备在验证前离线，横竖排截图尚未重新采集；几何单测和构建验证已完成。

## 设备验收限制

检测到 Android 16 平板（2136×3200，440dpi），但 debug APK 安装返回 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`。没有绕过该限制，也没有将旧版应用截图作为本次验证结果。

因此当前交付是已实现并经过构建/静态验证的代码；**像素观感、触控回归和截图矩阵仍待设备允许安装后验收**。

待验收矩阵：

| 页面 | 浅色/深色 | 手机 360/412dp | 平板 sw600dp |
| --- | --- | --- | --- |
| 首页、书架、批量选择 | 待截图 | 待检查 | 待检查 |
| 搜索、详情、设置、更多 | 待截图 | 待检查 | 待检查 |
| 横向/竖向阅读控制层 | 待截图 | 待检查 | 待检查 |

设备允许安装后检查主导航、搜索、卡片长按选择、详情继续阅读、主题切换、目录、进度和阅读设置；不需要执行删除书籍、投票、评论发送等写入操作来验证视觉。
