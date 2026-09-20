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
