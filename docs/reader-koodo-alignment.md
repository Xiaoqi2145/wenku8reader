# 阅读界面与 Koodo Reader 的对齐分析

参考源：`E:\Project\Codex\Project\SoraReader\reference\koodo-reader`（本地源码，`src/pages/reader`、`src/containers/panels`、`src/components/readerSettings`、`src/constants`）。

本文只做**阅读界面风格与阅读设置**两个维度的对齐分析，不重复 [`reader-ui-optimization-plan.md`](./reader-ui-optimization-plan.md) 已落地内容（批次 A、B1、B3、B4、B5、C、D1、D3、D4）。

---

## 一、Koodo Reader 阅读界面风格

### 1.1 结构：正文 + 四向浮动面板

阅读器是独立窗口，正文居中，四周为**四向浮动面板触发区**（`pages/reader/component.tsx`）：

| 位置 | 面板 | 尺寸 |
| --- | --- | --- |
| left | NavigationPanel（封面/书名/作者/阅读时长/搜索 + 4 个 tab） | 触发区 40px × 40% 屏高 |
| right | SettingPanel（Reading option） | 面板 299px 宽，全高 |
| top | OperationPanel（当前/剩余阅读时长 + 退出/书签/全屏） | 触发区 40% × 40px |
| bottom | ProgressPanel（进度/页码/章节 + 进度条） | 触发区 40% × 40px |

触发区样式统一：`rgba(75,75,75,0.3)`、20px 单侧圆角、默认 `opacity:0`，鼠标靠近边缘 500ms 唤出、移出 500ms 收起（`PANEL_ENTER_DELAY` / `PANEL_LEAVE_DELAY`）。另有 `isPreventTrigger` 配置项可把唤出方式从 hover 改为 click，防止误触。

面板支持**锁定常驻**：`isNavLocked` / `isSettingLocked`。锁定后面板背景切换为用户的阅读背景色（`backgroundColor` + `textColor`），与正文融为一体而非浮层。

正文周围还有两层悬浮快捷按钮（靠近边缘才显现）：右上角是缩放 / PDF 裁边 / PDF 转文字 / 网格（一键展开四面板）；右下是下一章 / TTS / AI / 批注；左下是上一章。

### 1.2 阅读设置（右侧 SettingPanel，自上而下）

`containers/panels/settingPanel/component.tsx` 的固定顺序是：**视图模式 → 主题色 → 滑杆 → 下拉 → 开关 → 更多菜单**。

**View mode**（`readerSettings/modeControl`）：`single` / `double` / `scroll` 三个图标按钮，未选中项 `opacity:0.4`。PDF 与 CB 格式走 `pdfReaderMode`（默认 `scroll`），其余走 `readerMode`（默认 `double`）。

**主题色**（`readerSettings/themeList`）分三段：

- Background color：预设 4 色 `白 / rgba(44,47,49,1) / rgba(233,216,188,1) / rgba(197,231,207,1)`，外加「自定义色」入口（HexColorPicker + hex/rgba 文本框），自定义色进入列表后可删除；标题右侧有 `Clear` 清除覆盖。
- Text color：预设 `黑 / 白 / rgba(89,68,41,1) / rgba(54,80,62,1)`，同样支持自定义。
- Theme：`KookitConfig.PresetThemeList` 预设的 bg+text 组合块，点击整组切换。

**滑杆**（`readerSettings/sliderList` + `constants/dropdownList.tsx` 的 `sliderConfigs`）：

| 项 | 范围 | 步长 | 默认 |
| --- | --- | --- | --- |
| Font size | 13–80 | 1 | 17 |
| Margin | -40–80 | 5 | 0 |
| Letter spacing | 0–20 | 1 | 0 |
| Paragraph spacing | 0–120 | 1 | 0 |
| Page width (scale) | 0.5–3 | 0.1 | 1 |
| Brightness | 0.3–1 | 0.1 | 1 |

每个滑杆都是「标题 + 可输入数值框 + 当前值 + min/max label + range」四件套，输入框支持键盘 Enter 与失焦提交，越界自动 clamp 回区间。

**下拉**（`readerSettings/dropdownList`）：Book layout（Default / kookit / 赫蹏 / 漢字標準格式 / 中文网页重设与排版 / Tufte / Typebase）、Code highlighting（30+ 语言）、Default font family / CJK font family、Line height（1–2）、Text alignment（Left / Justify / Right）、Text orientation（horizontal / vertical）、Conversion of Chinese（简繁互转）、Page turning animation（none / sliding / mimical）、Action after selecting text（翻译/词典/高亮/笔记/朗读）、Full text translation（禁用/双语/仅译文）。

**开关**（`readerSettings/settingSwitch` + `constants/settingList.tsx` 的 `readerSettingList`）：段落模式、PDF 双页起始、总页数、JS 执行、Hyphenation、保留 PDF 原始背景、允许孤行寡行、快速阅读模式（Bionic 加粗前半词）、首行缩进、加粗、斜体、下划线、阴影、反色、页边框、隐藏页脚、隐藏页眉、隐藏背景、自定义 CSS（textarea）、单本书独立样式、生词释义、阅读标尺（行高 0–20 + 背景透明度 0–1）、极速阅读（WPM 100–900）。

其中三条互斥：`isParagraphMode` / `isReadingRuler` / `isSpeedReading`（`exclusiveModeNames`）。`isBionic` 与生词释义互斥，冲突时弹 toast 阻止。

### 1.3 交互与反馈约定

- 每次设置变更 → `toast("Change successful")` → 500ms 后 `renderBookFunc()` 重渲染。
- 每个设置项都带 `isPDF` 字段，`SettingPanel` 按当前书的格式过滤选项，非 PDF 书不显示 PDF 专属项。
- 设置面板右上角 `···` 菜单提供 **Clear all style**：单书样式下删除该书的 `seperateStyleConfig`，全局样式下从 `localStorage.readerConfig` 批量删除 `KookitConfig.StyleKeys`。
- 全书搜索在左面板：搜索框切换为「Search in the Book」视图，结果按章节分组、组可折叠、显示总数、点击跳转并高亮命中。
- 顶部面板给出当前阅读时长与**剩余时长估算**（用相邻翻页间隔 `speed` 推算）。
- 左面板 4 个 tab：Content / Bookmark / Note / Highlight，书签、笔记、高亮都支持搜索与按进度排序。
- 进度面板信息密度最高：`进度 %` + `页 x/y`（可输入跳转）+ `章节 x/y`（可输入跳转）+ 上一章 / 进度条 / 下一章。
- 键位：`Ctrl+F` 全书搜索、`Ctrl+B` 目录、`Esc` 退出；20+ 动作可自定义快捷键。

---

## 二、当前项目阅读界面现状

### 2.1 结构

两个独立 Activity（`ui/read/vertical`、`ui/read/horizontal`），`CoordinatorLayout` 自下而上：正文 → 引导层 → `MaterialToolbar`（顶栏，菜单只有一个 `menu_hide_bar`）→ 常显状态行（28dp：左时间+电量 / 右百分比）→ 底栏 dock（进度行：上一章/滑块/下一章；操作行：夜间/设置/目录）。

- 设置面板：`BottomSheetDialog`，两组「显示设置 / 分页设置」。
- 目录：右侧抽屉 `ReadChapterCatalogBottomSheet`（`gravity=END`，宽 `min(屏宽×0.86, 420dp)`，`dimAmount=0.32`），封面 + 卷 rail + 章节列表，支持多选下载。
- 颜色：独立 `SelectColorActivity`，两个 `TextInputLayout`（文字色 / 背景色）+ 正则校验 hex，日间与夜间各一套，另有推荐色 chip。

### 2.2 设置项

| 项 | 竖排（`VerticalReadConfig`） | 横排（`HorizontalReadConfig`） |
| --- | --- | --- |
| 字号 | 1–50（sp），默认 16 | 30–100（画笔 px），默认 55 |
| 行距 | 1–50（额外 px），默认 1 | 1–5（倍数，步长 0.1），默认 1.5 |
| 进度文字 | — | 1–100，默认 45 |
| 保持常亮 | ✅ | ✅ |
| 平板双页 | — | ✅ |
| 按键翻章 | — | ✅ |
| 翻页动画 | — | boolean 开关 |
| 恢复章节阅读历史（+免确认） | ✅ | ✅ |
| 改色 | ✅ | ✅ |

竖排渲染只应用字号 + 行距 + 文字色/背景色（`ReadFragment.initView`）；章节标题用 `StyleSpan(BOLD)` + `RelativeSizeSpan(1.08f)`。

### 2.3 已确认的能力缺口

- 全项目 `grep` 无 bookmark / highlight / note 任何实现。
- 无全书搜索、无进度输入跳转、无阅读时长统计、无设置项重置、无单书独立样式。
- 「夜间」按钮（两个 Activity 均有）直接 `AppCompatDelegate.setDefaultNightMode`，**切换的是整个 App 主题**，与 `ReadColorConfig` 的阅读配色是两套互不相干的体系。
- 字号/行距滑杆在 `onStopTrackingTouch` 才提交，拖动中无实时预览。

---

## 三、对齐改进点

优先级定义：P0 = 阅读体验直接受益且改动可控；P1 = 能力补齐，改动较大；P2 = 打磨与长期项。

### P0-1 阅读配色与 App 主题解耦

现状：底栏「夜间」→ `setDefaultNightMode` 重建整个 Activity；阅读页文字/背景色来自 `ReadColorConfig` 的日/夜两套值。两者独立但被同一个按钮耦合，`reader-ui-optimization-plan.md` 的 P0-3 只给了状态行加背景这个**症状**处理，根因未解。Koodo 的模型是：App 主题色只管 UI accent，阅读 bg/text 完全独立于深浅主题。

落点：
- 新增 `ReaderThemeMode`（`Day` / `Night` / `System`）存进 `ReadColorConfig`，底栏按钮只切换阅读配色，不动 `AppCompatDelegate`。
- 阅读页配色决策收敛到一处（`ReadColorConfig` + `getIsInDarkMode` 的组合封装），替换 `ReadFragment.initView` 与两个 Activity 里的重复 `if (getIsInDarkMode())` 分支。
- 设置面板内联配色（色块 + 输入框），替换 `SelectColorActivity` 的整页跳转 + `replace(ReadFragment())` 重建。

验收：在深色 App 主题下选择白色阅读背景 + 黑色文字，正文与状态行、底栏均清晰可读；来回切换夜间按钮不触发 Activity 重建。

### P0-2 预设配色 + 自定义配色 + 一键还原

现状：只有两个裸 hex 输入框。Koodo 的三段式（预设 / 自定义 / 清除）是低成本高收益的对齐项。

落点：
- 预设组（4 组 bg+text，直接照搬 Koodo 数值）：`白+黑`、`深灰(2C2F31)+白`、`米黄(E9D8BC)+棕(594429)`、`浅绿(C5E7CF)+深绿(36503E)`。
- 当前组合可「保存」为自定义色（存 MMKV 列表，上限 8），点 ✕ 删除。
- 面板底部加「恢复默认」，语义对齐 Koodo 的 `Clear all style`（全局样式时清 `readerConfig`）。
- 控件用 `Widget.GalIsland.Button.Outlined` + 圆形色块，保持 GalIsland 视觉语言。

### P0-3 排版核心六项

当前只暴露字号 + 行距。对齐 Koodo 的高价值子集（PDF 专属项与 AI 项不需要）：

| 项 | 竖排建议 | 横排建议 | 现状 |
| --- | --- | --- | --- |
| 字号 | 保留 1–50 sp | 保留 30–100 px | ✅ |
| 行距 | 1–50 px（add） | 1–5 倍（步长 0.1） | ✅ |
| 段间距 | 0–40 px | 0–4 倍（步长 0.1） | ❌ |
| 字间距 | 0–20 px | 0–20 px | ❌ |
| 文字对齐 | 左 / 两端 | 左 / 两端 / 右 | ❌ |
| 首行缩进 | 0 / 2 字符 | 0 / 2 字符 | ❌ |

落点：段间距在 `VerticalRenderModelFactory` 生成文本时按段落插入间距（或 `setSpacing` span），字间距用 `TextView.setLetterSpacing`，对齐用 `setGravity`，缩进用 `FirstLineIndentSpan` 或段首追加空格。**继续遵守 `docs/galisland-visual-drift-audit.md` R-4 的横竖单位差异约定**，两个面板各自标单位，不要图省事统一范围。

### P0-4 滑杆数值可视化与实时预览

Koodo 每个滑杆都带数值框与 min/max label；当前滑杆只有轨道。同时字号/行距在松手才生效。

落点：
- 新增 `view_reader_slider_row.xml` 复用行：label + 数值（可点编辑）+ `Slider` + 范围提示，替代两块面板里 6 处重复的 `LinearLayout`。
- 字号/行距/段距/字距改为拖动中实时更新（`onValueChange`），并用现有 `VerticalScrollRestorer.restoreAfterLayout(anchor)` 保持锚点位置；横排同理但需在翻页后重新计算页码，沿用 `validatePageNum` 的越界回退。

### P1-1 翻页动画改三选一

现状是 `switchAnimation: Boolean`。Koodo 是 `none / sliding / mimical`。

落点：`HorizontalReadConfig` 改为 `switchAnimation: String`（默认 `none`），面板控件改为分段按钮（`MaterialButtonToggleGroup`），映射到 `ViewPager2` 的 `setPageTransformer`。MMKV 已有旧 boolean 值需要一次性迁移（`false → none`、`true → sliding`）。

### P1-2 进度面板信息密度

Koodo 底部一行给出 `进度%`、`页 x/y`、`章节 x/y` 且后两者可输入跳转。当前只有滑块 + 状态行百分比，两个口径还不一致（`reader-ui-optimization-plan.md` 的 P2-2 已记录该问题，D2 尚未落地）。

落点：
- 底栏进度行在滑块两侧补「章节 `x/y`」（横向另加「页 `x/y`」），点击章节文本打开目录并定位当前项。
- 统一语义：滑块表达**当前章节内进度**，状态行右侧改为「章节 x/y · 全书 xx%」。这与 D2 的目标一致，可一并落地。

### P1-3 书签（最小可用）

当前零实现，但这是阅读器的核心留存能力，且锚点基础设施已经齐备（`ReaderAnchor`、`reader_progress` 表、`ReaderProgressStore`）。

落点：
- 新增 `bookmark` 表（`aid, cid, anchor 各字段, excerpt(200字), createdAt`），复用 `ReaderAnchorMapper`。
- 顶栏菜单加「添加书签」，底部进度行旁加书签图标（有书签时高亮）。
- 目录面板增加「书签」tab，复用 `ReadChapterCatalogAdapter` 的列表结构，条目点击跳 `ReaderAnchor`。
- 笔记与高亮留到后续（需要先解决文字选区，见 P2-3）。

### P1-4 全书搜索

落点：目录面板顶部加搜索入口，切换为「全书搜索」视图；命中按章节分组、组可折叠、显示总数；点击跳转到该章节并按 `charOffset` 高亮。数据侧需要章节正文可检索——复用 `ChapterCache` 的磁盘缓存，未缓存章节按需网络加载后再搜，结果里标注来源。

### P2-1 阅读标尺（护眼阅读）

长文本阅读实用度高，实现成本低：`isReadingRuler` + `readingRulerLineHeight`（0–20）+ `readingRulerBackgroundOpacity`（0–1）。竖排在 `TextView` 上加一层跟随首行的半透明矩形覆盖（可用 `LineBackgroundSpan`）。

### P2-2 阅读时长统计

Koodo 顶部面板的「当前时长 + 剩余时长」。当前 `reader_progress` 已有 `updatedAt`，可以做「累计已读时长」并显示在目录面板标题区；剩余时长依赖翻页速率模型，优先级更低。

### P2-3 文字选区动作

Koodo 选中文字后可配置翻译/词典/高亮/笔记/朗读。当前 `TextView` 未开长按选择。这是高亮与笔记的前置，属长期项，建议先只开 `setTextIsSelectable`（并验证对滚动锚点与自动追加章节的影响）再逐步加动作。

### P2-4 单书独立样式

Koodo 支持按书覆盖全局样式（`seperateStyleBooks` + `seperateStyleConfig`）并提供清除入口。文库类作品不同书的排版需求差异大，值得做。落点：`ReadColorConfig` 与两个 `*ReadConfig` 增加 `byBook(aid)` 变体，`ViewModel` 读取时先查单书配置再回退全局。

### P2-5 手势分区

当前竖排只响应中央 1/3 点击，左右 1/3 无动作也无提示（`reader-ui-optimization-plan.md` P2-4 已记录）。Koodo 是四向唤出 + 可配置触发方式。触屏上更合理的对齐是**三分区**：左 1/3 上一章、右 1/3 下一章、中央 1/3 唤出菜单，并在首次引导层说明。落点：`VerticalTouchPolicy.tapArea` 与 `VerticalReaderTouchHandler`，需同步更新 `ReaderTouchJudge` 的测试。

---

## 四、不建议对齐的项

Koodo 是桌面/多格式阅读器（PDF/EPUB/TXT/CBR + AI 商业化），以下项对 wenku8 网络轻小说不适用，照搬会增加维护面：

- **PDF 专属**：页面宽度缩放（scale）、亮度、页边框、双页起始、保留原始背景、隐藏页眉/页脚/背景、裁边、转文字。
- **文档排版**：Book layout 预设（赫蹏/漢字標準格式/Tufte/Typebase）、代码高亮（30+ 语言）、Hyphenation、允许孤行寡行、自定义 CSS textarea、CJK 副字体、TXT 解析器。
- **多语言/AI**：全文翻译、生词释义（HSK/N/四六级）、词典与翻译插件、AI 对话、TTS 多音色、OCR 引擎选择、简繁转换（文库来源已是简体）。
- **交互形态**：四向 hover 唤出面板、面板锁定常驻、悬浮快捷按钮组。Android 触屏空间有限，点击分区（P2-5）比照搬 hover 更合适。
- **同步生态**：Notion/Obsidian/Readwise/Anki/Eudic/Discord Rich Presence/KOReader 进度同步。

---

## 五、建议落地顺序

| 顺序 | 项 | 理由 |
| --- | --- | --- |
| 1 | P0-1 配色解耦 | 解决既有方案的遗留根因，且是 P0-2 的前置 |
| 2 | P0-2 预设配色 + 还原 | 改动集中在设置面板，收益直接 |
| 3 | P0-4 滑杆数值行 + 实时预览 | 复用组件，顺带解决拖动无预览 |
| 4 | P0-3 排版六项 | 需改渲染链路，单独提交便于回归 |
| 5 | P1-1 翻页动画枚举 | 小改动，含一次 MMKV 迁移 |
| 6 | P1-2 进度语义统一 | 与既有方案的 D2 合并落地 |
| 7 | P1-3 书签 | 能力新增，独立模块 |
| 8 | P1-4 全书搜索 | 依赖章节正文检索链路 |
| 9 | P2-1 / P2-2 / P2-5 | 打磨 |
| 10 | P2-3 / P2-4 | 长期项 |

前 4 项都是阅读页可视层与配置层改动，不涉及章节拼接、锚点模型和数据迁移，回归面可控；第 7、8 项开始引入新数据表与网络检索，建议各自独立里程碑。

---

## 六、落地进度

### 6.1 已完成

上一提交 `fb9c691` 落地了 P0-1 / P0-2 / P0-4 / P1-1 的**配置层**，本轮补齐了 P0-3 的**渲染层**，并修掉配置层遗留的既有缺陷。

| 项 | 状态 | 落点 |
| --- | --- | --- |
| P0-1 配色解耦 | ✅ | `data/model/ReaderThemeMode.kt`、`util/ReaderColorResolver.kt`；底栏「夜间」只切 `readerThemeMode`，不再 `setDefaultNightMode`。第二轮补齐 chrome 跟随阅读配色与两态收敛，见第七节 |
| P0-2 预设 / 自定义 / 还原 | ✅ | `data/model/ReaderColorPreset.kt`、`ReadColorRepository.applyPreset/addCustomColor/restoreDefaults`；本轮补上预设色块（`util/ReaderColorSwatch.kt`） |
| P0-3 排版四项 | ✅ 本轮 | 竖排 `ui/read/vertical/VerticalTypography.kt`；横排 `PageView.kt:69-89`、`PageText.kt:49-52`、`PageLine.kt` |
| P0-4 滑杆数值行 + 实时预览 | ✅ | `res/layout/view_reader_slider_row.xml` + 两个 Activity 的 `setupSliderRow()` |
| P1-1 翻页动画枚举 | ✅ | `HorizontalReadConfig.switchAnimation` 含 boolean → string 迁移 |
| P1-2 进度语义统一 | ✅ 本轮 | `Novel.chapterProgressLabel()`（`data/model/Novel.kt:54`）为横竖排共用；章节计数可点开目录，页码可点输入跳转 |
| P1-3 书签 | ✅ 第三轮 | 表 / DAO / 增删 / 图标态 + 目录面板「书签」tab（`ReadBookmarkAdapter`） |
| P1-4 全书搜索 | ✅ 第三轮 | `BookSearchMatcher` + 两阶段扫描（缓存自动 / 联网显式），见第八节 |
| P2-1 阅读标尺 | ❌ | 未开始 |
| P2-2 阅读时长 | ❌ | 未开始 |
| P2-3 文字选区 | ❌ | 未开始 |
| P2-4 单书独立样式 | ❌ | 未开始 |
| P2-5 手势分区 | ✅ 第三轮 | 策略层早已存在，本轮补 handler 接线与测试 |

### 6.2 本轮修掉的既有缺陷

1. **`ReaderThemeMode.System` 永远解析成日间配色。** `ReaderColorResolver` 原来用
   `AppCompatDelegate.getDefaultNightMode() == MODE_NIGHT_YES` 判断系统深色，而默认
   `DarkMode.System` 映射到 `MODE_NIGHT_FOLLOW_SYSTEM(-1)`，永不等于 `MODE_NIGHT_YES`。
   这是相对旧 `getIsInDarkMode()`（读 `uiMode`）的**回退**。改为 `uiMode` 判定，构造函数
   增加 `Context` 参数（`util/ReaderColorResolver.kt:44`）。
2. **竖排「段间距」会把「行距」覆盖掉。** `ReadFragment` 原来在
   `ChangeParagraphSpacingEvent` 分支里直接 `tvFVRead.setLineSpacing(event.value, 1f)`，
   即拖段间距等于重设行距，而真正的段间距从未生效。
3. **竖排「首行缩进」是空操作。** 原分支只做 `scrollRestorer.restoreAfterLayout()`，没有任何渲染效果。
4. **横排「段间距 / 字间距 / 对齐 / 缩进」四项全部无效。** `PageView` 没有这四个属性，
   设置只写进 MMKV，渲染层从不读取。
5. **横排首行缩进是"假缩进"。** `splitContent` 给首行预留了 2 字符宽度，但 `drawPage`
   仍从同一起点画起 —— 首行只是变短，并没有缩进。
6. **竖排章节计数带字面反引号。** `ReadViewModel.chapterProgressText()` 返回
   `` "`3/120`" ``，反引号会原样显示在状态行。
7. **横排底栏章节计数从未更新。** `readChapterProgress` 只有竖排在写，横排一直显示布局默认值 `1/1`。
8. **横排书签按钮是占位实现**，点击只弹「书签功能即将上线」，与竖排行为不一致。
9. **`FakeReaderDao` 未跟上书签 DAO 方法**，导致 `:app:testDebugUnitTest` 的**整个测试源集无法编译**（自 `fb9c691` 起）。
10. **`PageImage` 居中测量用了错误的字号**：先以页脚字号 `measureText` 再切回正文字号 `drawText`，
    导致提示文案不居中。已改为先设字号再测量。

### 6.3 本轮实现要点

**竖排（`TextView` 连续滚动）**：新增 `VerticalTypography` + `VerticalTypographyApplier`，
四个设置项统一走一个 applier，实时预览与重渲染不可能分叉。

- 行距 → `setLineSpacing(extra, 1f)`（唯一作用于所有行的机制）
- 段间距 → 每段首字符挂一个 `LineHeightSpan`，`fm.ascent -= extra`（只加高该段首行）
- 首行缩进 → 每段挂 `LeadingMarginSpan.Standard(indent, 0)`
- 字间距 → `TextView.letterSpacing`（em，由 px / textSize 换算）
- 对齐 → `Gravity.START` + API 26 `justificationMode`（常量在公开 `android.jar` 中是 `@hide`，已镜像其值）
- **章节标题被排除**：`VerticalContentRenderer` 记录每章标题在拼接文本中的绝对区间，applier 跳过这些段落，避免给标题加缩进和段前距
- 全部用 span 实现，**不改变字符偏移**，`ReaderAnchor.charOffset` 在排版变更前后保持有效

**横排（自绘分页）**：行数据从 `String` 换成 `PageLine(text, indent, spacingBefore, isParagraphEnd)`。

- 分页由「固定行数」改为「按行高累积」（`PageView.kt:571`），否则段间距会把行挤出页底
- 字间距同时作用于测量与绘制，避免分行后溢出
- 首行缩进真正偏移 x；两端对齐按字符分摊空隙逐字绘制（CJK 无空格可拉伸）；段落末行不拉伸
- `HorizontalReadConfig.firstLineIndent` 默认值取 **2**（而非 0），因为横排原本硬编码 2 字符缩进，暴露开关不应改变既有渲染

### 6.4 未完成 / 后续

- **P2-1 阅读标尺 / P2-2 阅读时长 / P2-3 文字选区 / P2-4 单书独立样式**（P2-5 已在第三轮完成）。
- **横排搜索命中不高亮**：分页正文是 `Canvas` 自绘，加高亮要改分页/绘制链路，见 8.4。
- **阅读页的模态浮层仍跟随 App 主题**（设置面板 bottom sheet、首启引导卡片）。见 6.7 的边界说明。
- **两处 `failure.message ?: "加载失败"`**（两个 `ReadViewModel`）仍是硬编码中文，落在 `ReaderUiState.Error` 里，需要先改 `ReaderUiState` 才能本地化。

### 6.5 验证情况

- `:app:compileDebugKotlin` ✅
- `:app:testDebugUnitTest` ✅ 99 tests / 0 failures（新增 `VerticalTypographyApplierTest`、`NovelChapterProgressTest`、`ReaderChromePaletteTest`、`ReaderThemeModeTest`）
- `:app:assembleDebug` ✅
- `build/check_galisland_py.py` → 0 failure；`build/verify_res.py` → 0 XML 错误 / 0 悬空引用
- **像素观感未实测**：无可用设备/模拟器（安装受限见 `galisland-visual-drift-audit.md`）。
  段间距依赖 `LineHeightSpan.chooseHeight` 的行为、两端对齐的逐字绘制效果、chrome 换色后的观感，都属静态审查结论。
- **分页存在预期内的微差**：`linesPerPage` 由整数除法改为浮点累积，边界情况下每页行数可能相差 1 行。
  这是支持变行高的必要代价。
- 构建需显式 `export JAVA_HOME="D:/SDK/Java/jdk-21"`（系统默认 JDK 25 不被 Gradle 8.13 支持）。

---

## 七、第二轮：阅读页收敛到白天/黑夜 + chrome 跟随阅读配色

本轮目标是让阅读页**只有白天/黑夜两态**，且整页视觉（含顶栏、状态行、底栏）用同一套 GalIsland 语义角色表达，颜色全部来自阅读配色而非 App 主题。

### 7.1 配色收敛为两态

`ReaderThemeMode` 保留三个枚举值，但 `System` 降级为**纯遗留值**：

- 新增 `ReaderThemeMode.converge(systemIsDark)`：`System` → 与当前 App 主题一致的具象值（深色 App → `Night`，浅色 → `Day`），保证深色主题用户不会被翻成浅色阅读页。
- 新增 `ReadColorRepository.convergeReaderThemeMode(systemIsDark)`：一次性折叠并落盘，用 `ReadColorConfig.hasMigratedThemeMode` 把关（该 flag 此前定义后从未被使用）。两个 Activity 在 `initView()` 开头调用，先于任何配色读取。
- **`applyPreset` / `restoreDefaults` 不再写 `readerThemeMode = System`**。这两处是第三态"复活"的唯一来源：改色是颜色选择，不该顺手改主题模式；而 preset 会把日/夜两套写成同一组值，模式本来也影响不到显示。

### 7.2 chrome 跟随阅读配色

新增两个文件（`ui/read/common/`）：

- **`ReaderChromePalette`**（纯计算，可单测）：`surface` = 阅读背景色、`onSurface` = 阅读文字色、`onSurfaceVariant` = 向文字色混合 62%、`outline` = 混合 24%。二次色用**混合**而不是 alpha，好处是对任意 bg/text 组合都保证对比度，且结果不透明、不依赖叠加合成。
- **`ReaderChromeTheme`**：把调色板映射到 GalIsland 的语义角色上。

要点：

- **GalIsland 语言不变，只换值**：仍是「平面 + 1dp 细描边、无 elevation」，`surface` / `onSurface` / `onSurfaceVariant` / `outline` 四个角色一一对应。顶栏描边在**下缘**、底栏与状态行描边在**上缘**，两侧都与正文形成边界。
- 背景不能用 `setBackgroundTintList` 整体染色：`bg_read_bottom_bar.xml` / `bg_read_status_bar.xml` 是「面 + 描边」的 `layer-list`，整体染色会丢掉描边。改为运行时构造等价 `LayerDrawable`（两层 `GradientDrawable` + `setLayerGravity`）。
- 子树按类型染色：`MaterialButton`（文字按钮用 `onSurface`，纯图标按钮沿用更安静的 `onSurfaceVariant`，对齐 `Widget.GalIsland.Button.IconButton`）、`Slider`（`trackActive/Inactive` + `thumb` + `halo`）、`TextView`、`ImageView`。注意 `MaterialButton` 是 `TextView` 子类，匹配顺序必须先于 `TextView`。
- 菜单图标：`MaterialToolbar` 只暴露导航图标 tint，菜单图标需逐个 tint，因此放在 `onCreateOptionsMenu()` 里 inflate 之后执行（`ReaderChromeTheme.tintMenuIcons`）。
- `ReaderChromeController.shownBarColor` 由 `Int` 改为 `() -> Int`，并新增 `refreshSystemBars()`：切日夜或换配色后，系统状态栏/导航栏要跟着重绘（图标明暗本来就按亮度自动判定）。
- 顶栏原为**透明底**（颜色来自窗口背景），现在显式画上阅读背景，否则深色 App 主题下会在白页顶部留一条深色带。

### 7.3 校验脚本断言更新

`scripts/check-galisland.ps1`（及其 Python 等价 `build/check_galisland_py.py`）原有一条断言「阅读 Activity 必须出现 `giSurfaceReaderChrome`」。chrome 改走运行时调色板后该断言已失效，替换为**更强的不变式**：

- 必须引用 `ReaderChromePalette` 与 `ReaderChromeTheme`（颜色来源是调色板，不是主题属性）；
- 禁止出现 `#[0-9A-Fa-f]{6}` 硬编码色；
- 保留「禁止 `colorSurfaceContainer` 绕过语义 token」。

### 7.4 边界：模态浮层不跟随阅读配色

**阅读页的 bottom sheet（设置面板）与首启引导卡片继续跟随 App 主题。** 理由：

- 它们是浮层/卡片，自带 surface + 同色系文字，**在任何组合下都可读**（不存在"深色浮层压在浅色正文上"的读不清问题，只是色调与页面不同）。
- 让它们跟随阅读配色需要同时重染 `TextInputLayout` / `MaterialSwitch` / `MaterialButtonToggleGroup` / `Slider` / `gi_secondary_panel` 等一整套控件；只染 surface 不染内容反而会更糟（浅色面板 + 深色控件）。
- 主视觉（顶栏 / 状态行 / 底栏 / 正文）已经统一，浮层保持与 App 内其它 GalIsland sheet 一致，整 App 观感更连贯。

如需浮层也跟随，建议单独一个里程碑，并沿用 `ReaderChromeTheme` 的"角色映射"思路而不是逐控件打补丁。


---

## 八、第三轮：目录抽屉三 tab（书签 / 全书搜索）+ 三分区手势 + 死代码清理

### 8.1 目录抽屉改为三 tab

`fragment_read_chapter_catalog.xml` 从「封面 + 卷 rail + 章节列表」单视图改为 `TabLayout`（`Widget.GalIsland.TabLayout`，3dp 主色指示条）+ 三个内容容器：

| tab | 内容 |
| --- | --- |
| 目录 | 原样保留（封面 / 卷 rail / 章节列表 / 下载多选） |
| 书签 | `ReadBookmarkAdapter`：章节名 + 摘要 + 时间，右侧删除按钮（P1-3） |
| 搜索 | 输入框 + 进度行 + 按章节分组的结果列表（P1-4） |

面板加 `@AndroidEntryPoint` 注入 `ReaderRepository`。两个宿主 Activity（`NovelInfoActivity`、两个 `ReadActivity`）本来就是 Hilt 入口，不需要额外改动。

### 8.2 P1-3 书签 tab

- 表 / DAO / 增删 / 图标态在第二轮前已完成，本轮补的是列表 UI。
- `BookmarkEntity` 只存索引不存标题，所以由面板按 `(volumeIndex, chapterIndex)` 从 `Novel` 反查标题（`chapterTitleOf`），列表行用 `BookmarkRow(bookmark, chapterTitle)`。
- 点击 → 用书签里的 `paragraphIndex/charOffset/normalizedPercent` 重建 `ReaderAnchor` 跳转；删除走 `deleteBookmark` + 提示。

### 8.3 P1-4 全书搜索

**匹配**：`data/repository/reader/BookSearch.kt` 的 `BookSearchMatcher`（纯函数，可单测）。刻意不做分词 —— wenku8 是简体中文正文，大小写不敏感的子串扫描既准确又便宜。命中上限 30/章，摘要保留前后 24 字并把换行压成空格。

**两阶段执行**（关键设计）：

1. **阶段一（自动）**：`ReaderDao.cachedChapterContents(aid)` 一次查询取回全书已缓存正文，逐章匹配。**不联网。**
2. **阶段二（显式点按钮才跑）**：对未缓存章节调 `chapterLoader.load(ref)`（缓存优先、网络兜底，成功后自动入缓存），边扫边出结果，可随时取消。

为什么拆两阶段：一本书上千章，若搜索即触发全量联网，一次输入就会变成上千个请求。计划里写的"未缓存章节按需网络加载后再搜"是对的，但必须由用户显式确认，且结果里要标明来源 —— 所以状态行会显示"已扫描 x/y 章"，未覆盖的部分变成一颗「联网搜索剩余章节」按钮（运行中变为「取消」）。

**结果呈现**：按章节分组、组可折叠（折叠态存在面板，adapter 只做渲染）、显示总数。进度回写按 200ms 节流，扫描循环里每批 `yield()` 一次，避免长书扫描卡住主线程。

### 8.4 锚点跳转链路（三处点击共用）

新增 `ReaderJumpTarget(volumePos, chapterPos, anchor, highlightLength)`，面板回调统一为 `onPositionSelected`（替换原来的 `onChapterSelected`）。目录点击 anchor 为 null，书签点击带书签锚点，搜索点击带命中偏移 + 匹配长度。

- **竖排**：`ReadViewModel.setPendingJump/consumePendingJump`。`ReadFragment.restoreInitialPosition()` **优先消费 pendingJump**，再退回内存位置 / 持久化进度 / 旧历史 —— 目录跳转是显式意图，应当压过"上次读到哪"。消费后用 `scrollRestorer.restore(anchor)` 定位，并对搜索命中加一个临时 `BackgroundColorSpan`（2.5s 后移除）。
  - 高亮颜色由 `ReaderChromePalette` 的 `onSurface` 取 20% alpha 得到，**跟随阅读配色**，任意 bg/text 组合都可见。
  - 锚点的 `charOffset` 是章内偏移，用 `controller.globalOffset(anchor)` 换成拼接文本的全局偏移 —— 这也是排版用 span 而非改文本的收益之一。
- **横排**：分页阅读没有字符级光标，只用锚点的 `normalizedPercent` 换算页码，抽成 `applyPageFromPercent()`，与进度恢复共用同一条路径（避免两处口径漂移）。
  - **横排不做命中高亮**：正文是 `Canvas` 自绘的，加高亮需要改分页/绘制链路，收益不抵风险，留作后续。

### 8.5 P2-5 竖排三分区手势

计划里预估要改 `VerticalTouchPolicy` + `VerticalReaderTouchHandler` + 测试。实际情况：**策略层早就写好了三分区**（`tapArea` 返回 Previous/Center/Next），只是 handler 只接了 `onCenterTap`，左右两区被静默丢弃。

本轮改动因此很小：

- `VerticalTouchPolicy` 新增 `tapAction(...)`：把"是否点击 + 是否落在交互子视图上 + 落在哪个区"合成一次判定，返回 `VerticalTapArea?`；`shouldToggleReaderBar` 改为 `tapAction(...) == Center`，**策略只有一份真值**。
- `VerticalReaderTouchHandler` 增 `onPreviousChapter` / `onNextChapter`，`ACTION_UP` 按区派发。
- `ReadFragment` 接上 `toPreviousChapter()` / `toNextChapter()`（图片条目、翻章按钮等交互子视图仍独占触摸）。
- 首启引导文案改为说明三分区。

补了 3 组策略测试，其中一组做**交叉一致性**校验：遍历多种宽度与 x 比例，断言 `tapAction` 与 `shouldToggleReaderBar` 永不矛盾。

> **已被推翻（2026-09-21）**：三分区在实测体验里被判为"把翻页手势混进了滚动模式"——左右三分之一点击直接换章，
> 而手势分类只看净位移，滚动中点一下想停住惯性就可能跳章并带走阅读位置。
> 现已取消三分区：**全屏点击统一呼出/收起菜单**，换章只走显式控件（底栏按钮、正文首尾章节按钮、目录）。
> `VerticalTapArea` / `tapArea` / `tapAction` 一并删除，策略只剩 `shouldToggleReaderBar(isTap, startedOnInteractiveChild)`。
> 引导层也按模式拆成两份（横排左右三分之一翻**页**，竖排点击只切菜单）。核查与处置全过程见
> `docs/reader-gesture-mode-audit.md`。本节保留为第三轮的历史记录。

### 8.6 死代码清理

删除 `SelectColorActivity` / `SelectColorViewModel` / `activity_select_color.xml`，以及 `values` 与 `values-zh-rTW` 里的 `select_color_day`、`select_color_night`、`format_mismatch`，还有 `main`/`debug` 两个 manifest 里的 activity 条目。

删除前逐项核对：全项目无调用点；布局只引用共享的 `gi_*` token 与 `ic_info` / `cancel` / `save_config`，**没有独占 drawable 会变成死资源**；三个字符串除自身定义外零引用。删除后 XML 合法性校验通过。

> 遗留：`fragment_guide.xml:50` 有个 id 叫 `cv_a_select_color_recommend_day`，是 App 首启引导页自己的节点，与已删的配色页无关，只是命名撞车。本轮未改名（改 id 需要同步 Kotlin 引用），仅记录。

### 8.7 验证

- `:app:compileDebugKotlin` ✅ / `:app:assembleDebug` ✅
- `:app:testDebugUnitTest` ✅（新增 `BookSearchMatcherTest` 8 例；`VerticalReaderCoreTest` 增 3 例）
- `build/check_galisland_py.py` → 0 failure；`build/verify_res.py` → 0 XML 错误 / 0 悬空引用
- **仍未实测**：无设备。搜索的两阶段交互、书签跳转落点、三分区手感都属静态审查结论。
