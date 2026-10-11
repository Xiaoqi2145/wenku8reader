# 阅读器 P2 收尾计划

承接 [`reader-koodo-alignment.md`](../reader-koodo-alignment.md)。该文档第一~三节是对齐分析，第六~八节是已落地进度；本文只覆盖**尚未落地**的部分，给出落点、设计取舍、风险与建议顺序。

---

## 一、剩余项盘点

| 项 | 来源 | 改造面 | 可否静态验证 |
| --- | --- | --- | --- |
| P2-1 阅读标尺 | 计划 P2-1 | 竖排布局 + 面板 + 1 配置类 | ✅ 可 |
| P2-2 阅读时长 | 计划 P2-2 | 新增 MMKV + 计时 + 面板 | ✅ 可 |
| P2-4 单书独立样式 | 计划 P2-4 | 3 配置类 + 3 仓库 + 2 ViewModel | ✅ 可 |
| P2-3 文字选区 | 计划 P2-3 | 竖排可行，横排是大改 | ❌ **必须真机** |
| 模态浮层跟随阅读配色 | 文档 6.7 边界 | 一整套控件重染 | ⚠️ 观感类 |
| 剩余两处硬编码中文 | 文档 6.4 | 2 个 ViewModel + `ReaderUiState` | ✅ 可 |
| 3 个 dead dimens token | 校验脚本报告 | 1 文件 | ✅ 可 |
| `fragment_guide.xml` id 撞名 | 文档 8.6 遗留 | 1 布局（+ 可能 1 处 Kotlin） | ✅ 可 |

实测数据（用于估改造面）：

- 配置类属性数：`VerticalReadConfig` 9、`HorizontalReadConfig` 13、`ReadColorConfig` 8，共 **30**。
- 仓库公开 `get/set` 数：`VerticalReadRepository` 22、`HorizontalReadRepository` 30、`ReadColorRepository` 18，共 **70**（其中约 45 属样式类）。
- 三个仓库均为 `@Singleton`（`VerticalReadRepository.kt:9` 等）。
- 现有 MMKV 存储类 6 个，均为「单文件 + 扁平键」风格（`data/source/local/mmkv/`）。
- 全项目**没有任何**阅读时长/统计概念（grep `readingTime|阅读时长|累计` 无结果）。

---

## 二、P2-1 阅读标尺

### 目标

长文本阅读时给视线一个稳定的横向锚点。

### 方案取舍

| 方案 | 说明 | 结论 |
| --- | --- | --- |
| A 固定横带覆盖层 | 内容视口顶部放一条半透明横带，文字从其下滚过 | **推荐** |
| B `LineBackgroundSpan` | 在文本坐标里给「当前首行」画背景 | 不采纳：文本坐标随滚动移动，要让它固定在屏幕上仍需每次滚动重算，复杂度更高且无额外收益 |
| C 左右边缘遮罩（聚光带） | 只留中间亮区 | 不做，与 GalIsland 的"纸片"语言冲突 |

计划原文写的是「跟随首行的半透明矩形覆盖」——A 方案在**视口顶部**画横带，等价于"始终跟在首行位置"，且无需逐帧重算。

### 落点

- **布局**：`fragment_vertical_read.xml` 根节点由 `NestedScrollView` 外包一层 `FrameLayout`，内部追加
  `View id=rv_f_v_read_ruler`，`layout_gravity="top"`，`clickable=false`、`importantForAccessibility=no`。
  `FragmentVerticalReadBinding.root` 只被引用 2 处（`ReadFragment.kt:211` 设背景色、`:415` 触感反馈），换根安全。
- **Fragment**：新增 `applyReadingRuler()` —— 高度 = `binding.tvFVRead.lineHeight × lines`（`TextView.getLineHeight()` 已含行距，精确）；颜色由 `ReaderChromePalette` 的 `onSurface` 加 alpha 派生，**跟随阅读配色**；`setReaderContentInsets(top, _)` 里同步 `topMargin = top`，保证横带始终贴着内容视口顶。
- **面板**：`view_vertical_read_config.xml` 的「显示设置」分组加 1 个 `MaterialSwitch`（`Widget.GalIsland.Switch`）+ 2 个滑杆（复用 `view_reader_slider_row.xml`）：行数 1–20（默认 3）、透明度 5%–100%（默认 16%）。
- **配置**：`VerticalReadConfig` 加 `isReadingRuler: Boolean`、`readingRulerLines: Int`、`readingRulerOpacity: Float`。
- **横排不做**。分页是整页切换，固定横带没有意义；与 Koodo 的差异需在文档注明。

### 风险

低。唯一要守的约束是横带必须在 chrome 之下、正文之上（加在 `FragmentContainerView` 之后），且不拦截触摸。

### 验收

开关打开 → 竖排顶部出现半透明横带；调行数/透明度即时生效；滚动时横带不动、文字从下方滚过；关闭后消失；切换日夜配色时横带颜色跟随。

---

## 三、P2-2 阅读时长

### 目标

目录面板显示**本书累计已读时长**。

### 设计

- **存储**：新增 `ReadingStatsConfig`（`MMKV.mmkvWithID("reading_stats")`），键：`per_book_millis`（JSON map `aid → millis`，复用 `ReadColorRepository` 已有的 Gson 用法）。不逐键存 `book_millis_$aid`，因为聚合查询用 JSON map 更干净。
- **纯逻辑**：`reader/domain/ReadingTimeAccumulator.kt` —— `accumulate(current, elapsed)` + `format(millis)`（「2 小时 13 分」/「13 分」/「不到 1 分」）。放 `reader/domain/` 是项目里纯逻辑的既有位置，可单测。
- **计时**：`ReadActivity` 用 `onStart` / `onStop` 记时间戳并在 `onStop` 累加落盘。
  - **不要用 `onResume`/`onPause`**：`onPause` 在弹出 `BottomSheetDialog`（设置面板、目录抽屉）时**不会**触发（Dialog 不是 Activity），所以 `onStart/onStop` 恰好把"面板停留时间"算进阅读、把"切后台/锁屏"排除在外。这正是想要的语义。
- **显示**：`fragment_read_chapter_catalog.xml` 的 header 追加第 3 行 `tv_f_read_chapter_catalog_reading_time`（`TextAppearance.GalIsland.Meta`）。面板已注入 `ReaderRepository`，再加一个 `ReadingStatsConfig` 注入即可，无需改 `newInstance` 签名。
- **剩余时长不做**：Koodo 用相邻翻页间隔推算速率，且需要全书页数估计。单独立项。

### 风险

低。注意 `onStop` 里是同步 MMKV 写（很快，可接受）。

### 验收

读 3 分钟退出，重进目录面板显示「已读 3 分」；切后台不计时；多次会话累计叠加；换书显示各自时长。

---

## 四、P2-4 单书独立样式

### 目标

按书覆盖全局排版 / 配色（Koodo 的 `seperateStyleConfig`）。

### 方案取舍

| 方案 | 说明 | 结论 |
| --- | --- | --- |
| A 仓库持有作用域 | 仓库加 `setActiveBook(aid)`；配置类 getter/setter 加 `aid: String? = null`，键变 `"$aid:$name"`；ViewModel 载入 novel 时调一次 | **推荐**：~80 处调用点零改动 |
| B 调用点显式传 aid | 45 个方法签名加 `aid`，两个 ViewModel 各改 ~45 处 | 无隐式状态，但改动面大、易漏 |
| C 样式快照 | 启用时快照整条样式记录 | 仍需 A 或 B 才能读写，只解决存储形态 |

A 的代价：`@Singleton` 仓库带上可变作用域 = 隐式全局状态。风险可控（同一时刻只开一个阅读页），但**必须在 `onDestroy` 重置作用域**，并在文档写明这是有意为之。

另需一个「本书是否启用独立样式」开关。注意它**不能存在 per-book 命名空间里**（自指），应存在全局键 `"per_book_style_enabled_$aid"`。

### 落点

- 3 个配置类：每个属性的 MMKV key 加可选 aid 前缀。
- 3 个仓库：加 `setActiveBook(aid: String?)` + `clearBookStyle(aid)` + `isPerBookStyleEnabled(aid)` / `setPerBookStyleEnabled(aid, Boolean)`；内部所有配置访问透传作用域。
- 2 个 ViewModel：`novel` 赋值后调一次 `setActiveBook`；`onCleared` 重置。
- 两个设置面板：各加「本书独立样式」开关 + 「清除本书样式」按钮，并在开关打开时**把当前全局值复制为该书的初始值**（否则用户会看到样式跳变到默认值 —— 这是必须处理的细节）。
- 单测：key 前缀逻辑（纯函数抽取出来测），以及「启用时继承当前全局值」。

### 需要先决策

**覆盖范围**：只覆盖排版 + 配色（推荐，与 Koodo 的 style 语义一致），还是也覆盖「保持常亮 / 翻页动画 / 平板双页」这类行为开关？后者语义上更像"本书阅读偏好"而非"样式"，混在一起会让开关的含义变模糊。

### 风险

中。主要风险是「启用瞬间的样式来源切换」与「跨书串值」（A 方案下靠 `setActiveBook` 的调用时机保证）。

### 验收

A 书改字号 → B 书不受影响；关掉 A 书开关 → 回到全局值；清除本书样式 → 该书所有键被删除；横竖排各自独立且互不串值。

---

## 五、P2-3 文字选区（建议延后）

### 现状

- 竖排 `tvFVRead` 未开长按选择。
- 横排正文是 `Canvas` 自绘分页（`PageText.drawPage` 逐行 `drawText`），**没有可选择的文本层**。

### 竖排可行路径

1. `binding.tvFVRead.isTextSelectable = true`。
2. **必须同步**把 `tvFVRead` 加入 `VerticalReaderTouchHandler` 的 `interactiveChildren`，否则点选文字会同时触发翻章/呼出菜单。
3. `CustomSelectionActionModeCallback`：先把动作收敛为「复制」，高亮/笔记后续再挂。

### 为什么建议延后

- **唯一一个静态改完无法确认对错的项**。可选择的 `TextView` 嵌在 `NestedScrollView` 里，长按/拖动选择有可能抢走滚动手势（fling 失效），也可能与自动追加章节、锚点采样互相干扰 —— 这些只能真机回归。
- 横排要做选区，得把自绘分页换成 `StaticLayout` + 命中测试，属于独立的大改，且它是横排高亮/笔记的前置。
- 因此 P2-3 的产出形态建议是：**先只做"竖排可选 + 仅复制"，并在文档里标注需要真机回归**；横排另立里程碑。

---

## 六、收尾项（都是一两行级别）

1. **两处 `failure.message ?: "加载失败"`**（`ui/read/horizontal/ReadViewModel.kt:135`、`ui/read/vertical/ReadViewModel.kt:208`）。

   核实结论比预想的更简单：**`ReaderUiState.Error.message` 是只写不读的死字段。** 全项目对 `ReaderUiState.Error` 的引用只有三处 —— 两处构造（上述两行），一处 `is ReaderUiState.Error -> getNovelContent(...)` 的重试派发（`vertical/ReadViewModel.kt:157`），**没有任何地方读 `.message`**。UI 上显示的错误文案走的是 Event 侧（`Event.VerticalReaderLoadErrorEvent.message` / `Event.NetworkErrorEvent.msg`，Activity 里再 `?: getString(R.string.network_error_msg)` 兜底）。

   所以处理方式二选一：**(a)** 删掉这个字段（连带两处构造参数）；**(b)** 若想保留给未来的状态渲染用，就把它改成可空、去掉硬编码兜底。推荐 (a)：留着一个永远不显示的文案字段，只会让下一个人以为它在生效。

2. **3 个既有 dead dimens token**：`gi_card_padding`（`dimens.xml:40`）、`gi_status_bar_height`（`:82`）、`gi_text_card_title_emphasis`（`:97`）。确认零引用后删除。
3. **`fragment_guide.xml:50` 的 id `cv_a_select_color_recommend_day`**：与已删的配色页撞名。已核实 **Kotlin 侧零引用**，所以只是改个 id 字符串，无联动改动。

---

## 七、建议顺序

| 顺序 | 项 | 理由 |
| --- | --- | --- |
| 1 | P2-1 阅读标尺 | 独立、纯竖排、无数据层改动，风险最低 |
| 2 | P2-2 阅读时长 | 新增存储 + 计时，独立模块 |
| 3 | 收尾项 1/2/3 | 都是一两行级别，趁热清掉 |
| 4 | P2-4 单书独立样式 | 改造面最宽，需要专门一轮；开工前先定「覆盖范围」 |
| 5 | P2-3 文字选区 | **唯一必须真机验证**，且横排是大改；建议等有设备时再排 |
| 6 | 模态浮层跟随阅读配色（可选） | 需重染 `TextInputLayout` / `MaterialSwitch` / `MaterialButtonToggleGroup` / `gi_secondary_panel` 一整套控件；沿用 `ReaderChromeTheme` 的角色映射思路，不要逐控件打补丁 |

---

## 八、待决策

1. **P2-3 是否现在做**：我建议延后到有真机时。若现在做，产出范围限定为"竖排可选 + 仅复制"并显式标注未实测。
2. **P2-4 的覆盖范围**：只覆盖排版 + 配色（推荐），还是也覆盖行为开关（保持常亮 / 翻页动画 / 平板双页）？
3. **P2-1 是否也要横排**：计划里只写了竖排；若横排也要，需要先定义"分页模式下横带贴哪一页"。
