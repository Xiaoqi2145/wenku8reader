# 阅读页 UI 优化方案

范围：横向阅读（`ui/read/horizontal`）与竖向阅读（`ui/read/vertical`）的全部可视层，含顶栏、常显状态行、底栏、设置面板、目录面板与首次引导层。
本文同时记录方案、验收口径与当前实现状态。

截至 2026-09-13，批次 A、B1、B3、B4、B5、C、D1、D3、D4 已落地；本轮进一步统一 reader bottom dock 与动态窗口几何，目录保留宽屏侧滑抽屉以适配平板。批次 D2 的横向分页与竖向连续滚动仍保持各自原有进度口径，避免改变阅读历史数据语义。

本轮运行验收还修复了 MaterialAlertDialog 标题样式缺少布局尺寸的问题；代码层已补充 reader bottom dock、WindowInsets 协调和父坐标几何测试。平板首页、书架、详情和阅读页的像素观感、触控回归和截图矩阵仍需设备安装允许后验证。

---

## 一、现状盘点

### 1.1 层级结构（两模式一致）

`activity_vertical_read.xml` / `activity_horizontal_read.xml` 均为 `CoordinatorLayout`，自下而上：

| 顺序 | 视图 | 位置 | 背景 |
| --- | --- | --- | --- |
| 1 | 正文（`FragmentContainerView` / `PageView`） | 全屏 | 用户自定义阅读背景色 |
| 2 | 引导层（仅横向 `view_horizontal_read_guide.xml`） | 全屏 | 半透明遮罩 |
| 3 | 顶栏 `MaterialToolbar` | 顶部 | `?attr/giSurfaceToolbar` |
| 4 | 常显状态行 `view_read_status_bar.xml` | `bottom` | **无背景** |
| 5 | 底栏 `view_read_bottom_bar.xml` | `bottom` + `marginBottom=28dp` | `?attr/colorSurfaceContainer` + 1dp 顶描边 |

底栏结构：进度行（上一章 / 滑块 / 下一章）+ 操作行（夜间 / 设置 / 目录 / 隐藏的"更多设置"）。

### 1.2 关键数值

`values/dimens.xml`：`gi_status_bar_height=28dp`、`gi_status_icon=14dp`、`gi_control_height=48dp`、`gi_toolbar_height=56dp`、`gi_page_inset=16dp`、`gi_rail_width=80dp`。
样式来源统一为 `Widget.GalIsland.*` / `TextAppearance.GalIsland.*`。

### 1.3 交互现状

- 收起/呼出：横向仅点击屏幕正中（`IPageView.OnCenterClick`）触发；竖向仅点击**中央 1/3**（`VerticalTouchPolicy.tapArea`），左右 1/3 无任何动作。
- 隐藏入口：顶栏菜单 `menu_hide_bar`（全屏图标）。
- 设置面板：`BottomSheetDialog` + `view_vertical_read_config.xml` / `view_horizontal_read_config.xml`。
- 目录面板：`ReadChapterCatalogBottomSheet`，实际为 `DialogFragment` + `Dialog`，`gravity=END`，宽 `min(屏宽×0.86, 420dp)`，`dimAmount=0.32`。

---

## 二、问题清单

按严重度排序。P0 = 明确缺陷；P1 = 一致性与规范问题；P2 = 打磨项。

### P0-1 返回键直接退出阅读

代码全量检索 `onBackPressed` / `onBackPressedCallback` 在两个 `ReadActivity` 中**均无实现**。

后果：控制层处于显示态时按返回键会直接 `finish()` 退出阅读，与主流阅读器"返回键先收起控制层、再按才退出"的惯例相反，误触成本高（退出会触发进度 flush 与会话销毁）。

### P0-2 常显状态行遮挡正文末行

`view_read_status_bar.xml` 是贴底的 28dp 常显层，但：

- `gi_status_bar_height` 仅被两个 Activity 用于底栏 `layout_marginBottom`，**从未用作正文的底部内边距**（检索确认）。
- `fragment_vertical_read.xml` 的 `NestedScrollView` 无 `paddingBottom`；横向 `PageView` 按全高排版。

后果：正文最后一行/最后一张图会被状态行覆盖；竖向滚到底时"已经到底了"提示与"下一章"按钮也落在被遮挡区。

### P0-3 常显状态行无背景色，与自定义阅读背景冲突

状态行是无背景的裸 `LinearLayout`，文字颜色取 `?android:attr/textColorSecondary`（`TextAppearance.GalIsland.StatusText`）。

而阅读背景/文字颜色由用户在 `SelectColorActivity` 自定义（`getBgColorDay/Night`、`getTextColorDay/Night`），**与主题色相互独立**。

后果：用户在浅色主题下选择深色阅读背景（或反之）时，状态行文字会直接压在不可控底色的正文上，出现对比度不足甚至不可读。底栏用不透明主题色把正文遮住、状态行却透明处理，二者逻辑也不一致。

### P0-4 横向引导层未接入主题体系

`view_horizontal_read_guide.xml`：

- 根 `alpha="0.8"`，三张 `MaterialCardView` 用 `?attr/colorOnSurfaceVariant` 作底、`cornerRadius=0dp`、`strokeWidth=1dp`。
- 文字硬编码 `android:textColor="#ffffff"`。

冲突点：GalIsland 规范是 8dp 圆角 + `cardBackgroundColor=?attr/colorSurface` + `outlineVariant` 描边；此处 0 圆角 + 中灰底 + 纯白字，浅色主题下白字压中灰，深色主题下同样发灰，且 `cornerRadius=0` 与全局形状层级完全脱节。文案还把引导内容塞进了"菜单"卡片（`menu_tip`），语义错位。

另有：**竖向阅读完全没有引导层**，而竖向恰恰是"点击中央呼出菜单"这一隐式手势更不直观的模式。

### P1-1 底栏存在重复入口与死代码

`view_read_bottom_bar.xml` 中 `read_config`（"更多设置"）声明为 `android:visibility="gone"`，但：

- `vertical/ReadActivity.kt` 与 `horizontal/ReadActivity.kt` 都给它设了点击事件，且指向与 `read_settings` **同一个** `bottomSheetDialog`。
- 两个 `setBottomBarIsEnable` 也都在操作它。

属于死代码 + 语义重复，`more_setting` 字符串可一并下线。

### P1-2 控制层逻辑在两个 Activity 中重复约 150 行

`showBar()` / `hideBar()` / `getHideBarColor()` / `refreshStatusBar()` / `setBottomBarIsEnable()` / `parsePercent()` / 30 秒轮询协程，在横向与竖向 `ReadActivity` 中逐行重复（含完全相同的位移算法 `root.height - bar.top`）。

后果：任何一处调整都需双改，已经出现过"位移按实际位置计算"这类需要同步修正的逻辑，漂移风险高。

### P1-3 同一页面两套弹层范式

设置面板走 `BottomSheetDialog`（底部升起），目录面板走 `Gravity.END` 的侧滑 `Dialog`。

后果：同一阅读页内两种弹层手感、遮罩浓度（0.32 vs 默认）、圆角与出入场动画均不一致；且目录面板命名 `BottomSheet` 却非 bottom sheet，命名与实现背离。

### P1-4 目录面板缺少长篇必要能力

- 卷分组标题是纯 `TextView`（`item_read_chapter_catalog_header.xml`），**不可折叠**。
- 无章节搜索、无"回到当前章节"入口（仅在打开时 `scrollToPositionWithOffset` 一次）。

后果：文库类作品动辄数百章，滚动成本高。

### P1-5 竖排正文首尾按钮未走组件体系

`fragment_vertical_read.xml` 使用原生 `<Button>`，无任何 style；左右 `layout_margin=gi_rail_width(80dp)`、`match_parent` 全宽。

后果：与全局 `Widget.GalIsland.Button.Outlined` 观感不一致（默认 Material 圆角、无 7dp 形状、无 12sp 加粗字），且全宽按钮在正文流中占比过大，阅读节奏被打断。

### P1-6 竖排正文颜色硬编码

`vertical/ReadFragment.kt` 用 `Color.parseColor("#" + viewModel.getTextColorNight())` 直接设色，未经过主题 token；`rvFVRead` 的图片项 `item_vertical_read_image.xml` 固定 `280dp × 420dp` + `scaleType="centerCrop"`。

后果：小屏（360dp）下图片宽度接近满屏且被裁切；大屏下过窄，未按可用宽度自适应。

### P2-1 状态行 30 秒轮询

两个 Activity 都用 `while(true) { refreshStatusBar(); delay(30_000) }`。

后果：时间显示最多滞后 30 秒（跨分钟时明显），电量同理；且进入页面、切章时不会立即刷新。

### P2-2 两个"进度"口径不同

- 底栏滑块：竖向按**已加载内容的滚动比例**（`ReadFragment.scrollToPercent`），横向按**章节页数**（`valueTo=maxPageNum`）。
- 状态行百分比：竖向来自 `VerticalProgressReporter.displayPercent`，横向来自 `pageNum/maxPageNum`。

后果：同一屏出现两个含义不同的百分比，且横向滑块 `stepSize` 未声明（默认连续）而竖向为 `stepSize=1`，控件行为也不统一。

### P2-3 文案硬编码与语义切换

`fragment_vertical_read.xml` 内 `android:text="已经到底了"` 未走 `strings.xml`；代码又在两处把它改成 `R.string.continue_reading`。同一控件在"已经到底了 / 继续阅读"之间切换，短语义不清（是本章到底还是全书到底）。

### P2-4 缺少触觉反馈与手势提示

章节切换、滑块吸附、控制层呼出均无 `HapticFeedback`。竖向左右 1/3 点击区无动作也无提示，用户无法从视觉推断可点击区域。

---

## 三、优化方案

分四批交付，每批可独立验收。建议顺序即为优先级。

### 批次 A：缺陷修复（P0，不动观感）

| 编号 | 改动 | 落点 |
| --- | --- | --- |
| A1 | 新增 `OnBackPressedCallback`：控制层显示 → `hideBar()`；已隐藏 → 退出；加载中屏蔽 | 两个 `ReadActivity`，逻辑收敛到 A4 的基类 |
| A2 | 正文底部预留安全区：竖向 `NestedScrollView` 加 `paddingBottom = gi_status_bar_height + gi_space_sm`；横向 `PageView` 排版高度扣减同一数值 | `fragment_vertical_read.xml`、`PageView` 测量逻辑 |
| A3 | 状态行加背景与描边：`background = ?attr/colorSurfaceContainer`，顶部 1dp `?attr/colorOutlineVariant`（复用 `bg_read_bottom_bar.xml` 的 layer-list 思路，新建 `bg_read_status_bar.xml`） | 新建 drawable + `view_read_status_bar.xml` |
| A4 | 抽 `ReadChromeController`（或 `BaseReadActivity`）：统一持有 `showBar/hideBar/refreshStatusBar/setBottomBarIsEnable/getHideBarColor/返回键`，两个 Activity 只做绑定 | 新建于 `ui/read/common/`，两处 `ReadActivity` 改为委托 |

验收：按返回键不退出；滚到正文最底端时末行文字与"下一章"按钮完全可见；浅色主题 + 深色阅读背景组合下状态行文字清晰可读。

### 批次 B：一致性收敛（P1）

| 编号 | 改动 |
| --- | --- |
| B1 | 删除 `read_config` 按钮、其点击回调、`setBottomBarIsEnable` 中的引用；下线 `more_setting` 字符串（若他处未用） |
| B2 | 目录面板统一为 Material 底部弹层（`BottomSheetDialogFragment`），或反向统一为侧滑；二选一并同步另一个，`dimAmount`、圆角、出入场动画取一致值。建议统一为底部弹层，符合单手操作模型 |
| B3 | 目录补能力：卷标题改为可折叠分组（`ExpansionPanel` 或分组头 + 折叠态），标题栏加"回到当前章节"按钮 |
| B4 | 竖排首尾按钮改用 `Widget.GalIsland.Button.Outlined` + `app:icon`，宽度改为内容自适应并居中，左右边距降为 `gi_page_inset` |
| B5 | 竖排图片项改为 `layout_width=0dp + weight` 或 `maxWidth = 屏宽 - 2*gi_page_inset`，保留 2:3 比例，`scaleType` 评估 `fitCenter` 以避免裁切 |

验收：`scripts/check-galisland.ps1` 通过；两个模式下的底栏、面板、按钮在浅/深色下形状与描边一致。

### 批次 C：引导层重构（P0-4）

| 编号 | 改动 |
| --- | --- |
| C1 | 废弃全屏遮罩式三卡引导。改为一次性轻提示：`MaterialCardView`（`colorSurfaceContainerHigh` 底、8dp 圆角、`outlineVariant` 描边、`onSurface` 文字）+ 一行说明 + "知道了/不再提示"，置于正文中部或底部 3/4 处 |
| C2 | 竖向补齐同类提示："点击屏幕中央可呼出菜单"。两种模式共用一个 `view_read_tip.xml`，文案走 `strings.xml`，仅显示一次（复用现有 `isHorizontalReadFirstLaunch` 的持久化方式，扩展为按模式记录） |
| C3 | 引导文案与 `menu_tip` 解耦：把"点击图片正中可放大"归入图片放大提示，而非"菜单"卡片 |

验收：浅色/深色主题下引导层与正文对比清晰；首次进入显示、二次进入不再出现；不再有硬编码 `#ffffff`。

### 批次 D：打磨（P2）

| 编号 | 改动 |
| --- | --- |
| D1 | 时间刷新改为对齐分钟边界（`delay(60_000 - System.currentTimeMillis() % 60_000)`），进入页面与切章立即刷新一次；电量改用 `ACTION_BATTERY_CHANGED` 监听 |
| D2 | 统一进度语义：底栏滑块一律表达"当前章节内进度"，状态行右侧显示"章节 x/y · 全书 xx%"；横向滑块显式声明 `stepSize=1` |
| D3 | 端到端文案化：`已经到底了` 抽为 `strings.xml`（区分"本章结束"与"全书结束"两种状态） |
| D4 | 触觉反馈：切章成功、滑块 `onStopTrackingTouch`、控制层呼出/收起各一次轻触反馈（`HapticFeedbackConstants.KEYBOARD_TAP`） |
| D5 | 竖向呼出区域可视化：首次提示中说明"点击屏幕中央"；条件允许时在点击中央时给一次极短的分区高亮（可选，若影响沉浸则放弃） |

---

## 四、需要新增/调整的资源

新增（建议）：

- `values/dimens.xml`：`gi_reader_status_line_height`（沿用 28dp，语义化）、`gi_reader_content_bottom_inset`（= 28dp + 8dp）、`gi_reader_action_row_height`（56dp）
- `drawable/bg_read_status_bar.xml`：`colorSurfaceContainer` 底 + 顶部 1dp `colorOutlineVariant` 描边
- `layout/view_read_tip.xml`：统一轻提示
- `ui/read/common/ReadChromeController.kt`

复用：`Widget.GalIsland.Button.Outlined`、`Widget.GalIsland.CardView`、`TextAppearance.GalIsland.StatusText`、`TextAppearance.GalIsland.SheetTitle`。

保持不变的既定约定（用户已多轮确认，不在本次改动范围）：

- 状态行**常显**，不随上下栏收起。
- 左侧时间 + 电量、右侧阅读百分比，**保持左右分列**，不合并为居中胶囊。
- 底栏位于状态行之上（`marginBottom = gi_status_bar_height`）。
- 收起位移按实际位置计算（`root.height - bar.top` / `-tb.bottom`），A4 抽取时保留该算法。

---

## 五、验收口径

### 5.1 静态

```powershell
.\scripts\check-galisland.ps1
GRADLE_USER_HOME="E:/Project/Codex/Project/Novel/wenku8reader/.workbuddy/gradle-home" JAVA_HOME="C:/Program Files/Java/jdk-17" ./gradlew :app:assembleDebug --offline
```

要求：视觉资源扫描通过；Lint 不新增 error；`view_horizontal_read_guide.xml` 中 `#ffffff` 与 `cornerRadius=0dp` 归零。

### 5.2 截图矩阵（设备允许安装后）

| 场景 | 浅色 | 深色 |
| --- | --- | --- |
| 竖向 · 控制层显示 | 待检查 | 待检查 |
| 竖向 · 控制层收起（状态行常显） | 待检查 | 待检查 |
| 竖向 · 滚动到最底端 | 待检查 | 待检查 |
| 横向 · 控制层显示 / 收起 | 待检查 | 待检查 |
| 设置面板 / 目录面板 | 待检查 | 待检查 |
| 浅色主题 + 深色阅读背景（对比度用例） | — | 待检查 |

尺寸覆盖：手机 360dp、412dp；平板 sw600dp。

### 5.3 触控回归清单

1. 控制层显示时按返回键 → 收起控制层，不退出。
2. 控制层隐藏时按返回键 → 退出。
3. 竖向点击中央 1/3 → 呼出/收起；点击左右 1/3 → 不误触发。
4. 滚到正文最底 → 末行文字、"下一章"按钮、状态行三者互不遮挡。
5. 目录跳章、上一章/下一章、进度滑块拖动 → 状态行百分比与滑块同步。

---

## 六、风险与取舍

| 项 | 风险 | 处置 |
| --- | --- | --- |
| A2 正文底部预留 | 横向 `PageView` 按高度分页，扣减高度会改变分页结果，已保存的 `pageNum` 可能与新分页错位 | 扣减后需让 `validatePageNum` 的越界回退逻辑覆盖此场景；或仅在末页追加留白 |
| A3 状态行加背景 | 与"阅读页尽量沉浸"的取向有张力，可能被评价为"多了一条栏" | 用 `colorSurfaceContainer` + 单像素描边，高度维持 28dp 不加高；若用户不接受可退化为仅在自定义背景与主题色对比不足时启用 |
| B2 目录改底部弹层 | 侧滑面板在平板上更省屏，改底部弹层可能削弱平板体验 | 只在手机（< sw600dp）统一为底部弹层，平板保留侧滑并同步样式 |
| A4 抽公共控制器 | 触及两个 Activity 的主流程，回归面大 | 纯行为等价搬迁，不加新功能；搬迁后先跑触控回归清单再改观感 |

---

## 七、建议落地顺序

1. A1（返回键）+ A3（状态行背景）：改动小、收益直接，可作为一个提交。
2. A2（正文底部预留）：需验证分页错位，单独提交。
3. A4（公共控制器抽取）：结构治理，独立提交、纯搬迁。
4. B1/B4/B5：清理与组件对齐。
5. B2/B3（目录面板）：体验改动最大，最后做。
6. C 批次引导层重构。
7. D 批次打磨。
