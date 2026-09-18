# GalIsland 视觉风格偏移审查

审查日期：2026-09-18
基准：`E:\Tool\Galisland-UI-Template\styles\{tokens,layout,components}.css` + `docs/galisland-visual-spec.md`
方法：静态源码审查（资源 XML + 布局 + Kotlin），**未做设备像素验收**

---

## 0. 结论摘要

现有的 `scripts/check-galisland.ps1` 覆盖的断言**全部通过**。但该脚本的断言集中在「语法层」（有没有写死数字、有没有用未映射样式），而视觉风格偏移主要发生在「语义层」（同一视觉角色在不同位置是否用同一个值）。

本次共定位 **8 项确凿偏移**，按影响面分级：

| 级别 | 编号 | 问题 | 影响面 |
| --- | --- | --- | --- |
| P0 | 1 | 小说封面比例：网格 4:5，列表/目录 7:10 | 列表页、目录页所有封面 |
| P0 | 2 | 目录封面 elevation 3dp，规范要求卡片 0dp | 阅读目录 |
| P1 | 3 | `gi_card_stroke` 定义后 0 引用，选中态强调描边未生效 | 全部网格卡片 |
| P1 | 4 | 跨语义域复用 token（7 处），改 token 会误伤无关间距 | 历史/标签/崩溃/书架/登录 |
| P1 | 5 | `gi_metric_*` 伪语义 token 110 处，架空 dimens 的「唯一数值来源」 | 20+ 布局文件 |
| P2 | 6 | styles 层 30+ 处硬编码尺寸，同文件内写法自相矛盾 | 全部组件样式 |
| P2 | 7 | 卡片样式继承链重复覆盖 | 小说卡片 |
| P2 | 8 | `values-night/themes.xml` 冗余重复 Base 主题 | 主题维护 |
| P2 | 9 | `MaterialSwitch` 10 处使用未映射的 M3 默认样式（规范明令禁止） | 设置页 + 阅读面板 |

> **修复状态（2026-09-18 更新）**
>
> 本报告的 **P0-1 / P0-2 / P1-3 / P1-4 / P2-6 / P2-7 / P2-8 / P2-9** 以及 **R-1 ~ R-6** 已全部修复，
> 逐项修复方式、文件位置与验证结果见 **`docs/galisland-visual-drift-fix.md`**。
>
> 验证：`check-galisland.ps1` 全部断言通过；`:app:assembleDebug` BUILD SUCCESSFUL。
>
> **P1-5（`gi_metric_*` 伪语义 token）未修**——26 个 token、约 110 处引用，且同一 token 在不同位置
> 承担不同视觉角色，无法靠改名解决；多数值不落在 `gi_space_*` 梯级上，归入梯级即改视觉，而当前
> 设备无法验收。建议单独立项，方法与推进顺序见 fix 文档。
>
> 另：**P1-4 的规模已修正**——初版只报 7 处，实际扫描后 `gi_control_icon`(20dp) 被系统性当作通用
> 间距使用共 **49 处**，`gi_status_icon` 5 处、`gi_grid_unit` 1 处。详见 fix 文档。
>
> **R-4 已更正**：初版把横竖排面板的滑块参数差异判为「分叉」属误判，实为底层单位不同（横排画笔
> px / 行距倍数，竖排 sp / 额外行距 px），范围与步长必须不同。详见 R-4 节。

另有 2 项观察项（卡片按压态、网格线取色时机），需设备实测确认，不计入偏移。

**补充：阅读页与主视觉的体系落差**（第 R 系列，见下文）

上一轮审查只看「是否偏离规范」，没有横向对比「阅读页与主视觉是否同一套视觉语言」。补查后新增 6 项落差。核心结论：阅读页的 **chrome 层（顶栏 / 底栏 / 状态行 / 目录）已完全接入 GalIsland token**，但 **设置面板层没有**——它是唯一一处「自带一套布局语言」的界面。

| 级别 | 编号 | 问题 |
| --- | --- | --- |
| P1 | R-1 | 阅读设置面板无分组容器，主视觉二级页有「纸片面板」层次 |
| P1 | R-2 | 分组标题未用 `TextAppearance.GalIsland.SectionTitle`，字号与主视觉差 1sp |
| P1 | R-3 | 行高与间距手法不同：主视觉行高 56dp + 行内 padding，阅读面板无行高 + 行间 margin |
| P2 | R-4 | 横排 / 竖排设置面板差异**多为有意设计**（底层单位不同）；残留 id 命名不统一与缺注释 |
| P1 | R-5 | 同一面板两个同级分组标题间距不一致（12dp vs 14dp），后者用了语义无关 token |
| P2 | R-6 | 阅读页工具栏未用 `Widget.GalIsland.Secondary.Toolbar`，且重复声明样式已有属性 |

---

## P0-1 小说封面比例：网格 4:5 vs 列表/目录 7:10

**规范依据**：`docs/galisland-visual-spec.md:41` —「小说封面比例：4:5」，未区分网格与列表。

**证据**：

| 位置 | 实际值 | 比例 |
| --- | --- | --- |
| `layout/item_novel_cover_grid.xml:18` | `app:layout_constraintDimensionRatio="4:5"` | 4:5 ✅ |
| `values/dimens.xml:31-32` | `gi_cover_width=112dp` / `gi_cover_height=160dp` | **7:10** ❌ |
| `values/galisland_cards.xml:10-11` | `galisland_novel_list_cover_width/height` → 指向上者 | 7:10 ❌ |
| `layout/item_novel_cover_liner.xml:16-17` | 列表卡片封面用上者 | 7:10 ❌ |
| `layout/fragment_read_chapter_catalog.xml:73-74` | 目录封面 112dp × 160dp | 7:10 ❌ |

全项目仅 `item_novel_cover_grid.xml:18` 一处声明了 4:5。

**影响**：同一本书的封面在网格视图中按 4:5 呈现，切到列表或目录后被裁成 7:10，纵向多裁掉约 14% 画面高度（`centerCrop` 裁切）。封面通常是书名/作者排版区，裁切会切掉构图。

**建议**：`gi_cover_height` 由 160dp 改为 **140dp**（112 × 5/4），使三处统一为 4:5。若列表卡确实需要更高封面，应在规范中显式写明例外并另立 token，而不是让通用 token 偏离。

**注意**：`layout/fragment_logging_in.xml:12`、`layout/fragment_login_error.xml:13` 也用 `gi_cover_height` 作图片高度，但它们不是封面，改值会同时影响登录页插图高度——需一并确认。

---

## P0-2 目录封面 elevation 3dp

**规范依据**：`docs/galisland-visual-spec.md:40` —「卡片 elevation：0dp」，无例外条款。

**证据**：
- `values/dimens.xml:69` — `gi_catalog_cover_elevation = 3dp`
- `layout/fragment_read_chapter_catalog.xml:78` — `app:cardElevation="@dimen/gi_catalog_cover_elevation"`

全项目唯一一处非 0 elevation。

**影响**：在「零高程 + 细描边 + 网格底纹」的体系里，这是唯一带投影的卡片，会在目录抽屉中产生突兀的浮起感。且 `Base.Theme.HikariNovel` 设了 `elevationOverlayEnabled=false`（`values/themes.xml:30`），该 elevation 只投影、不叠色，视觉上是纯阴影。

**建议**：若目录封面确需与正文区分，用 `giSurfaceReaderChrome` 表面色或描边强度区分，而非 elevation；否则清零。

---

## P1-3 `gi_card_stroke` 是死 token

**证据**：
- `res/color/gi_card_stroke.xml` 定义了 selected / focused → `?attr/colorPrimary` 的描边选择器
- 全项目 `res/` + `java/` 引用数 = **0**

**与文档不符**：`docs/galisland-visual-alignment.md:9` 声称已实现「右上角选择角标**与强调描边**」。实际上：
- `values/galisland_cards.xml:16` — `Widget.GalIsland.NovelCards.Card` 用的是静态 `strokeColor = ?attr/colorOutlineVariant`
- 选中态只靠 `SelectionIndicator`（`layout/item_novel_cover_grid.xml:38-58`）的 `colorPrimary` 勾图标表达

**影响**：卡片选中时没有规范承诺的主色描边，与模板 `.library-card.is-selected { border-color: var(--accent); box-shadow: 0 0 0 2px var(--accent-soft) }` 相比，选中反馈弱一档。

**建议**：把 `Widget.GalIsland.NovelCards.Card` 的 `strokeColor` 改为 `@color/gi_card_stroke`；若确认要放弃该设计，则删除 `gi_card_stroke.xml` 并修订 `galisland-visual-alignment.md` 的表述。**二者必须择一**，不要保留「定义了但不接线」的中间态。

---

## P1-4 跨语义域复用 token

把某个语义域的 token 挪去表达另一个语义域。数值恰好相同，所以**视觉上目前无害，但 token 的语义边界已破**——一旦按规范调整原 token，会静默改动无关的间距。

| 位置 | 写法 | token 本义 | 实际用途 |
| --- | --- | --- | --- |
| `layout/item_visit_history.xml:42` | `layout_marginBottom="@dimen/gi_radius_tag"` | 标签圆角 6dp | 标题与时间的间距 |
| `layout/item_tag.xml:9` | `layout_marginEnd="@dimen/gi_radius_tag"` | 标签圆角 | 标签间距 |
| `layout/activity_crash.xml:33` | `layout_marginTop="@dimen/gi_radius_tag"` | 标签圆角 | 页面间距 |
| `layout/fragment_bookshelf_content.xml:18` | `layout_margin="@dimen/gi_radius_tag"` | 标签圆角 | 列表外边距 |
| `layout/item_visit_history.xml:29` | `layout_marginLeft="@dimen/gi_control_icon"` | 控件图标 20dp | 文本左边距 |
| `layout/item_visit_history.xml:25` | `layout_height="@dimen/gi_cover_width"` | 封面宽度 112dp | 缩略图高度 |
| `layout/fragment_logging_in.xml:12` | `layout_height="@dimen/gi_cover_height"` | 封面高度 160dp | logo 高度 |

**重点风险**：`gi_radius_tag` 被 4 处当间距用。规范若调整标签圆角（例如 6dp → 8dp 对齐卡片），这 4 处间距会跟着变，且没有任何检查会发现。

**顺带**：`item_visit_history.xml` 内部就自相矛盾——`:29` 左边距用 `gi_control_icon`(20dp)，`:30` 右边距用 `gi_space_inset`(10dp)，同一行的左右边距来自两个不同语义域。

**建议**：按目标间距值替换为 `gi_space_*` 梯级；`gi_cover_width` 作高度、`gi_cover_height` 作 logo 高度两处，另立语义 token（如 `gi_thumbnail_height`、`gi_brand_mark_height`）。

---

## P1-5 `gi_metric_*` 伪语义 token

**证据**：
- `values/galisland_layout_metrics.xml` 定义 26 个 token，命名形如 `gi_metric_156dp`、`gi_metric_5dp`——**只描述数值，不描述用途**
- 全项目引用约 **110 次**，跨 20+ 布局文件
- 使用最多的：`gi_metric_5dp`(24)、`gi_metric_30dp`(10)、`gi_metric_3dp`(8)、`gi_metric_18dp`(8)、`gi_metric_60dp`(6)

**一个 token 承担多种视觉角色**，例：

`gi_metric_5dp` 同时用于 —
- `layout_margin`（四边，如 `activity_account.xml:63`）
- `layout_marginTop`（如 `fragment_guide.xml:81`）
- `padding`（如 `fragment_login_error.xml:37`）
- `paddingTop` + `paddingBottom`（如 `item_comment.xml:32-33`）

`gi_metric_30dp` 同时用于 — `layout_margin`、`layout_marginLeft/Right`、`layout_marginBottom`、`fadingEdgeLength`、`layout_height`。

**与设计意图冲突**：`values/dimens.xml:3-5` 文件头自称「GalIsland 视觉体系：唯一数值来源」「组件样式只引用本文件的 dimen」。同时 `gi_space_xxs/xs/sm/md/lg/xl`（2/4/8/12/16/24dp）这套真正的间距梯级存在，但 `gi_metric_5dp`(5dp) 不在梯级内——说明这批值是从旧代码直接搬运，绕过了新体系。对照 `galisland_secondary.xml` 的 `gi_secondary_page_margin/panel_padding/row_height`，语义化写法在项目里已有先例。

**关键副作用——检查失效**：`scripts/check-galisland.ps1:14` 的断言是

```powershell
if ($text -match 'android:(textSize|padding\w*|layout_margin\w*)="[1-9][0-9]*(?:\.[0-9]+)?(?:dp|sp)"')
```

它只拦「直接写裸数值」。把数值搬进资源名（`gi_metric_5dp`）后，这个断言**恒为绿**。于是「0 处内联尺寸」这个漂亮的结果，有相当一部分是靠改换写法达成的，而不是靠收敛到语义梯级。**检查通过 ≠ 规范落地**——这是本次审查最值得注意的一点。

**建议**：
1. 按视觉角色把 `gi_metric_*` 逐个归类到 `gi_space_*` / `gi_control_*` / `gi_text_*` 梯级；
2. 确实不属于任何梯级的一次性尺寸，改为语义命名（如 `gi_login_brand_height`）；
3. 检查脚本增加断言：禁止新增 `gi_metric_` 前缀 token。

---

## P2-6 styles 层 30+ 处硬编码尺寸

**规范盲区**：`docs/galisland-visual-spec.md:43` 只约束「布局和 drawable 不得新增硬编码视觉尺寸」，**styles 不在约束范围内**。而检查脚本也只扫 `layout*/*.xml`（`check-galisland.ps1:8`）。

**实际存在硬编码的位置**（`values/galisland_styles.xml`）：

| 行号 | 内容 | 已有对应 token |
| --- | --- | --- |
| 29 | `android:textSize 14sp` | `gi_text_card_title`(14sp) |
| 34 | `android:textSize 11sp` | `gi_text_meta`(11sp) |
| 39 | `android:textSize 12sp` | `gi_text_body`(12sp) |
| 44 | `android:textSize 13sp` | `gi_text_section`(13sp) |
| 50/65 | `android:textSize 10sp` | — |
| 55/69 | `android:textSize 12sp` | `gi_text_body` |
| 60 | `android:textSize 13sp` | `gi_text_section` |
| 73 | `android:textSize 16sp` | — |
| 83/211/223/232/265/276 | `strokeWidth 1dp` | `gi_stroke`(1dp) |
| 115/116/118 | Slider `4dp/8dp/16dp` | — |
| 200/201 | `insetTop/Bottom 4dp` | `gi_space_xs` |
| 243 | `tabIndicatorHeight 3dp` | — |
| 254/255 | nav indicator `64dp/32dp` | — |

**同文件内自相矛盾**（最能说明问题的一处）：
- `galisland_styles.xml:29` — `TextAppearance.GalIsland.CardTitle` 写死 `14sp`
- `galisland_styles.xml:135` — `TextAppearance.GalIsland.NovelTitle` 用 `@dimen/gi_text_card_title`（值同为 14sp）

两者语义相同、数值相同、写法不同。`dimens.xml` 里 `gi_text_*` 系列 token 已经备好，只是早期定义的这批样式没回填——说明资源化是分批推进的，前一批未收尾。

**建议**：把上表逐项替换为对应 token（缺的补 token）；规范第 2 节把约束范围从「布局和 drawable」扩展到「布局、drawable 与 styles」，检查脚本相应增加对 `values/galisland_*.xml` 的扫描。

---

## P2-7 卡片样式继承链重复覆盖

`Widget.GalIsland.NovelCards.Card`（`values/galisland_cards.xml:12-19`）的 parent 是 `Widget.GalIsland.CardView.Novel`（`values/galisland_styles.xml:98-104`），后者已定义：

```
cardCornerRadius / cardElevation 0dp / strokeWidth @dimen/gi_stroke
cardBackgroundColor ?attr/colorSurface / strokeColor ?attr/colorOutlineVariant
```

子样式又**原样重复**了其中 4 项：

```xml
<item name="cardBackgroundColor">?attr/colorSurface</item>
<item name="cardElevation">0dp</item>
<item name="strokeWidth">@dimen/gi_stroke</item>
<item name="strokeColor">?attr/colorOutlineVariant</item>
```

**影响**：冗余覆盖使继承关系不可信——改父样式不生效、改子样式不知影响谁。同时 `0dp` 与 `@dimen/gi_stroke` 的写法在父子间不一致（父用 dimen，子用字面量）。

**建议**：删除子样式中的重复项，只保留真正差异化的属性。

---

## P2-8 `values-night/themes.xml` 冗余重复

- `values-night/themes.xml` 共 30 行，只定义 `Base.Theme.HikariNovel`
- 内容与 `values/themes.xml:4-31` 功能等价（均为 `?attr/colorSurfaceVariant` 这类动态 attr 引用）
- 昼夜差异实际由 `values-night/colors_galisland.xml` 的 color 变体承担，不依赖此文件

**影响**：同一份 Base 设置在两个文件重复维护，任一侧新增属性都会静默漂移（例如往 `values/themes.xml` 的 Base 加一条，夜间不会生效，除非同步改 values-night）。

**建议**：删除 `values-night/themes.xml`（若确认昼夜无需差异化 Base 配置），或改为只写真正的差异项。

---

## P2-9 MaterialSwitch 使用未映射的 M3 默认样式

**规范依据**：`docs/galisland-visual-spec.md:52` 明确列为禁止项——「使用未映射的 Material3 默认组件样式」。

**证据**：
- `values/themes.xml` 已映射 12 个 M3 组件样式：`materialCardViewStyle`、`materialButtonStyle`、`chipStyle`、`tabStyle`、`toolbarStyle`、`sliderStyle`、`textInputStyle`、`snackbarStyle`、`snackbarTextViewStyle`、`materialAlertDialogTheme`、`appBarLayoutStyle`、`bottomSheetDialogTheme`
- **唯独没有 `materialSwitchStyle`**
- 全项目 `MaterialSwitch` 共 **10 处**，且全部**不带 `style` 属性**（0 处声明）

分布：

| 文件 | 数量 |
| --- | --- |
| `layout/activity_setting.xml` | 1 |
| `layout/view_horizontal_read_config.xml` | 6 |
| `layout/view_vertical_read_config.xml` | 3 |

**影响**：这 10 个开关走 M3 默认尺寸（轨道 / thumb / 状态层），与 GalIsland 的精细尺寸体系（控件 48dp、图标 20dp、圆角 6/7/8/10dp）不匹配，视觉上明显比周围控件「胖」一档。阅读面板里开关紧邻 `TextAppearance.GalIsland.ConfigLabel` 标签，反差尤其突出。

这是**全局问题**（主视觉设置页同样中招），不限于阅读页——所以它不属于 R 系列，独立列为 P2-9。

**建议**：新增 `Widget.GalIsland.Switch`（基于 `Widget.Material3.CompoundButton.MaterialSwitch`，收敛 thumb / track 尺寸与 `gi_radius_*`），并在主题注册 `materialSwitchStyle`。

---

# R 系列：阅读页与主视觉的体系落差

## 前置结论：chrome 已统一，面板没统一

先说明查清的部分，避免误伤：

阅读页的 **chrome 层已完全接入 GalIsland token**，与主视觉同源：

| 部件 | 实现 | 判定 |
| --- | --- | --- |
| 底栏底板 `drawable/bg_read_bottom_bar.xml` | `giSurfaceReaderChrome` + 1dp `colorOutlineVariant` 描边 | ✅ |
| 状态行底板 `drawable/bg_read_status_bar.xml` | 同上 | ✅ |
| 底栏控件 `layout/view_read_bottom_bar.xml:30,37,47,63,72,81` | `Widget.GalIsland.Button.IconButton` / `.Text.Icon` / `Slider` | ✅ |
| 状态行 `layout/view_read_status_bar.xml` | `gi_status_icon` / `gi_space_*` / `TextAppearance.GalIsland.StatusText` | ✅ |
| 目录面板 `drawable/bg_read_chapter_catalog_panel.xml` | `giSurfaceSidebar` + 1dp 描边 + `gi_radius_window`(10dp) | ✅ |
| 目录项 `drawable/bg_read_catalog_item.xml` | `giAccentSoft`(选中) / `giSurfaceHover`(按压) / `gi_radius_control` | ✅ |
| 顶栏 `layout/activity_*_read.xml:17-19` | `Widget.GalIsland.Toolbar` | ✅（写法见 R-6） |

**落差集中在设置面板**：`layout/view_horizontal_read_config.xml`、`layout/view_vertical_read_config.xml`。这是全项目唯一一处没有接入主视觉布局语言的界面。

---

## R-1 设置面板无分组容器

**主视觉做法**（`layout/activity_setting.xml:29-34`）：

```xml
<LinearLayout
  android:background="@drawable/gi_secondary_panel"
  android:orientation="vertical"
  android:paddingBottom="@dimen/gi_space_md">
```

`drawable/gi_secondary_panel.xml` = 透明底 + 1dp `colorOutlineVariant` 描边 + `gi_radius_card`(8dp) 圆角。作用是在网格上划出一张「纸片」，把设置项成组框住。这是 GalIsland 视觉体系里区分信息组的**主要手法**。

**阅读面板做法**：整份布局 0 个 CardView、0 处 `gi_secondary_panel`（已核对 `view_horizontal_read_config.xml`、`view_vertical_read_config.xml`）。所有设置项直接平铺在 bottom sheet 背景上，没有任何分组边界。

**影响**：同一批设置项，在主视觉页面里是「纸片上的分组」，在阅读面板里是一张没有边界的长列表。用户从设置页进入阅读设置会感到风格断裂。

**建议**：把「显示设置」「分页设置」两组分别包进 `@drawable/gi_secondary_panel` 容器，与 `activity_setting.xml` 一致。

---

## R-2 分组标题未用样式 token

| | 主视觉 `activity_setting.xml:36-43` | 阅读面板 `view_horizontal_read_config.xml:40-47, 116-123` |
| --- | --- | --- |
| 写法 | `android:textAppearance="@style/TextAppearance.GalIsland.SectionTitle"` + 覆盖 `textColor` | 纯内联 `textColor` + `textStyle`，**无 textAppearance** |
| 字号 | 13sp（来自 token） | 未指定 → 默认 14sp |
| 颜色 | `?colorPrimary` | `?colorPrimary` |
| 粗细 | bold（样式 + 内联重复声明） | bold |

`TextAppearance.GalIsland.SectionTitle` 已在 `values/galisland_styles.xml:43-48` 定义（13sp / bold / `colorOnSurface`）。阅读面板 0 处引用（已核对两个面板）。

**影响**：分组标题字号差 1sp，且因为没走 token，后续统一调整标题层级时**阅读面板不会跟随**。

**建议**：两处标题改用 `TextAppearance.GalIsland.SectionTitle`，颜色覆盖保留为 `?attr/colorPrimary`（注意补 `attr/` 前缀，见 R-5）。

---

## R-3 行高与间距手法不同

**主视觉行**（`activity_setting.xml:44-53`）：

```xml
android:paddingBottom="@dimen/gi_space_sm"   <!-- 8dp -->
android:paddingTop="@dimen/gi_space_sm"      <!-- 8dp -->
android:minHeight="@dimen/gi_secondary_row_height"  <!-- 56dp -->
android:paddingRight="@dimen/gi_space_lg"    <!-- 16dp -->
android:paddingLeft="@dimen/gi_space_lg"     <!-- 16dp -->
```

行用 **padding（行内）+ minHeight** 撑开，所以每行高度恒定 56dp，节奏整齐。行内图标用 `gi_space_xl`(24dp)。

**阅读面板行**（`view_horizontal_read_config.xml:70-77` 等）：

```xml
android:layout_marginLeft="@dimen/gi_page_inset"   <!-- 16dp -->
android:layout_marginRight="@dimen/gi_page_inset"
android:layout_marginTop="@dimen/gi_space_inset"   <!-- 10dp -->
```

用 **margin（行间）** 分隔，**无 minHeight**。行高由内容决定——滑块行高 ≈ 48dp、开关行高 ≈ M3 默认开关高度，两者不等，导致行距疏密不匀。

**影响**：横向内边距两边一致（都是 16dp ✅），但纵向节奏不同：主视觉恒定 56dp，阅读面板随控件类型浮动。视觉上阅读面板更"挤"，且触控目标更小。

**建议**：给阅读面板的行补 `android:minHeight="@dimen/gi_secondary_row_height"`，间距手法改为与 `Widget.GalIsland.Secondary.Row` 一致。项目已有现成样式 `Widget.GalIsland.Secondary.Row`（`values/galisland_secondary.xml:16-22`：minHeight + 左右 padding + `selectableItemBackground`），阅读面板可以直接复用。

---

## R-4 横排 / 竖排两个设置面板的差异（**已更正：多数为有意设计**）

> **更正说明**：本节初版把两个面板的滑块参数差异判为「分叉」。后续查证底层单位后确认——**这些差异是有意设计，不构成偏移**。原判断有误，现更正如下，并保留其中真实存在的两项小问题。

两个面板是**两份独立维护的布局**（各自被 `ViewHorizontalReadConfigBinding` / `ViewVerticalReadConfigBinding` 绑定），不是同一份 include。差异清单：

| 项目 | 横排 | 竖排 | 判定 |
| --- | --- | --- | --- |
| 字号滑块范围 | `30–100` | `1–50` | ✅ 有意（单位不同，见下） |
| 行距滑块 | `stepSize=0.1`，`1–5` | `stepSize=1`，`1–50` | ✅ 有意（单位不同） |
| 分组标题 | 「显示设置」+「分页设置」 | 只有「显示设置」 | ✅ 有意（竖排无分页类设置项） |
| 进度文字大小滑块 | 有 | 无 | ✅ 有意（竖排无页脚，见下） |
| 平板双页 / 按键翻章 / 翻页动画 | 有 | 无 | ✅ 有意（竖排为连续滚动，无分页概念） |
| 颜色按钮 id | `b_v_h_read_config_color` | `b_v_h_read_config_change_color` | ❌ 命名不统一 |

**为什么范围差异是有意的——底层单位确实不同**：

字号：
- 横排 `HorizontalReadConfig.fontSize` 默认 `55f`，直接赋给 `PageView.textSize`（`PageView.kt:47` 注释：「正文部分默认画笔的大小」），在 `PageText.kt:104` 参与 `i * mTextSize * mRowSpace` 排版 → **画笔 px**
- 竖排 `VerticalReadConfig.fontSize` 默认 `16f`，最终作为 `TextView.setTextSize` 值 → **sp**

行距：
- 横排 `lineSpacing` 默认 `1.5f`，赋给 `PageView.rowSpace`，在 `PageText.kt:104` 的用法是 `i * mTextSize * mRowSpace` → **倍率**（1–5 合理，0.1 步长合理）
- 竖排 `lineSpacing` 默认 `1f`，走 `ReadFragment.kt:58,130` 的 `TextView.setLineSpacing(value, 1f)` → 第一个参数是**额外行距 px**（1–50 合理，1 步长合理）

所以「同一个 `@string/line_spacing` 标签下，横排是倍率、竖排是像素」——**范围与步长必须不同**，强行统一会破坏渲染。

「进度文字大小」同理：横排该设置对应 `PageText.drawFooter()` 绘制的**页内页脚**（章节名/页码）字号（`PageView.bottomTextSize`，默认 45f）；竖排是连续滚动、没有页脚，因此无需该设置。

**仍然存在的问题**：

1. **按钮 id 命名不统一**——`b_v_h_read_config_color` vs `b_v_h_read_config_change_color`，同一功能的两个 id 用词不同，视图绑定代码里容易混淆。
2. **差异缺注释说明**——两个布局里没有任何注释解释「为什么横排范围是 30–100、竖排是 1–50」。下一个维护者（或下一次自动化比对）会重蹈本节初版的误判，把有意设计当成漂移去「修」。这是比 id 命名更值得处理的问题。

**建议**：
- 统一按钮 id 命名；
- 在两个面板的滑块节点上补注释，写明单位（横排：画笔 px / 行距倍率；竖排：字号 sp / 额外行距 px）。**不要**统一取值范围。

---

## R-5 同一面板两个分组标题间距不一致

`view_horizontal_read_config.xml` 内两个同级标题：

| 位置 | 标题 | 间距写法 | 实际值 |
| --- | --- | --- | --- |
| `:44` | 显示设置 | `android:paddingTop="@dimen/gi_space_md"` | 12dp ✅ 间距 token |
| `:120` | 分页设置 | `android:paddingTop="@dimen/gi_status_icon"` | **14dp** ❌ 语义无关 |

`gi_status_icon` 的语义是「阅读状态行图标尺寸」（`values/dimens.xml:43`，14dp），被挪来当分组标题的上边距用。这与 P1-4 的跨语义域复用是同一类问题，但**后果更直接**：同一面板里两个视觉角色完全相同的标题，间距差了 2dp。

**建议**：`:120` 改为 `@dimen/gi_space_md`，与 `:44` 对齐。

**顺带**：`android:textColor="?colorPrimary"` 缺 `attr/` 前缀（全项目 28 处无前缀 / 60 处有前缀）。语法上 `?colorPrimary` 合法且等价，但同一项目两种写法并存，属于风格不统一。

---

## R-6 阅读页工具栏写法与主视觉不统一

| | 主视觉二级页（如 `activity_setting.xml:9-22`） | 阅读页（`activity_vertical_read.xml:17-26`） |
| --- | --- | --- |
| 外层 | `AppBarLayout` + `Widget.GalIsland.AppBar` | 无 AppBarLayout |
| 工具栏样式 | `Widget.GalIsland.Secondary.Toolbar`（7 处使用） | `Widget.GalIsland.Toolbar`（3 处） |
| 冗余声明 | 无 | 手写 `android:minHeight="@dimen/gi_toolbar_height"` + `android:elevation="0dp"`，两项样式里已有 |

`Widget.GalIsland.Secondary.Toolbar`（`values/galisland_secondary.xml:8-11`）的 parent 就是 `Widget.GalIsland.Toolbar`，只多一条 `elevation 0dp`。阅读页用基类 + 手写 elevation，结果**视觉等价**，所以这不是观感缺陷，而是**写法分叉**——同一套视觉角色存在两条实现路径，后续改样式容易漏改一边。

**建议**：阅读页统一改用 `Widget.GalIsland.Secondary.Toolbar`，并删掉布局里样式已声明的 `minHeight` / `elevation`。

---

## 观察项（未实测，不计入偏移）

**O-1 卡片无按压态视觉反馈**
模板 `.library-card:hover` 有 `border-color: border-strong` + `background: bg-panel-hover` + `box-shadow: shadow-panel` + `translateY(-3px)`。Android 侧 `Widget.GalIsland.NovelCards.Card` 仅继承 M3 Filled 的 ripple，描边与背景在按压时无变化。触屏无 hover，属合理适配，但 `giSurfaceHover` token 已存在且仅用于 chip 与目录项（`res/color/gi_chip_background.xml:4`、`res/drawable/bg_read_catalog_item.xml:11`），未用于卡片。是否补齐属产品取舍。

**O-2 网格线颜色在构造期固化**
`ui/other/LibrarySurfaceLayout.kt:19-23` 与 `ui/other/GalislandGridLayouts.kt:16-20` 都在**构造函数**里执行 `MaterialColors.getColor(view, R.attr.giGridLine)`。Activity 重建时 View 重建、颜色正常；但若存在复用 View 实例的换肤路径，网格线色不会跟随。需实测确认，建议改为在 `onDraw` 中解析或在主题变更回调里刷新。

---

## 已做对的部分（修改时不要误伤）

这部分是本次审查中确认**正确**的设计，修复时应保留：

1. **布局层已彻底资源化**——63 个布局文件，内联 Material 样式 0 处、内联尺寸 0 处、内联颜色 0 处。这是实打实的成果。
2. **29 个页面全部接入网格表面根容器**，无遗漏。
3. **网格绘制顺序正确**——页面根设 `android:background="?android:attr/colorBackground"` 作为底色，网格在 `onDraw` 中绘制（背景之后），因此网格不会被纯色背景盖住。这解释了为什么每页允许 1 处 `colorBackground` 是安全的。
4. **Token 色值与模板逐值吻合**——`colors_galisland.xml` 昼夜两套与 `tokens.css` 完全对齐：accent `#BC5E96`/`#DC91BD`、teal `#3D8D80`/`#7FC6BA`、amber `#9B7622`/`#D6B76E`、danger `#C64E55`/`#E78388`；outline alpha `0x24`=14%、outlineVariant `0x14`=8%，与 `--border-strong`/`--border` 一致。
5. **圆角梯级一致**——6/7/8/10dp 对应模板 tag/control/card/window。
6. **阅读器 chrome 用语义 token**——`bg_read_bottom_bar.xml` / `bg_read_status_bar.xml` 均用 `giSurfaceReaderChrome` + `colorOutlineVariant` 描边，符合规范第 4 节。
7. **Kotlin 层无违规硬编码颜色**——仅出现在阅读正文用户配色（`ReadFragment.kt:133-139`），规范第 3 节明确允许。
8. **卡片样式已收敛**——3 个 item 布局统一走 `Widget.GalIsland.NovelCards.Card`，未出现散落定义。
9. **明暗切换机制正确**——`Theme.HikariNovel.GalIsland` 虽无显式 parent，但隐式父链 `Theme.HikariNovel`（`values-v23/themes.xml:2`）→ `Base.Theme.HikariNovel` 成立，且 minSdk=24 ≥ 23，继承完整。
10. **阅读页 chrome 层已完全接入 token**——底栏 / 状态行 / 目录面板 / 目录项 / 顶栏全部使用语义 token，与主视觉同源（详见 R 系列前置结论）。落差只在设置面板，不要连带改动 chrome。

---

## 建议的检查脚本增强

现有 `scripts/check-galisland.ps1` 的断言可保留，建议补充以下 5 条，把「语义层」纳入检查：

1. **封面比例断言**：布局中凡引用 `gi_cover_width`/`gi_cover_height` 作为宽高对的，校验比例 = 4:5。
2. **禁止新增伪语义 token**：`values/galisland_layout_metrics.xml` 中不允许出现新的 `gi_metric_` 前缀；已有数量只减不增。
3. **跨语义域引用拦截**：布局中禁止 `layout_margin*`/`padding*` 引用 `gi_radius_*`、`gi_control_icon`、`gi_cover_*`；禁止 `layout_height` 引用 `gi_cover_width`。
4. **死 token 检测**：`res/color/*.xml`、`values/dimens.xml` 中定义的选择器/尺寸若引用数为 0，报告为待清理。
5. **styles 硬编码扫描**：把 `check-galisland.ps1:14` 的尺寸正则从 `layout*/*.xml` 扩展到 `values/galisland_*.xml`。
6. **elevation 白名单**：除白名单外，`elevation`/`cardElevation` 必须为 `0dp`。
7. **未映射 M3 组件检测**：扫描布局中出现的 `com.google.android.material.*` 控件，若主题未注册对应的 `xxxStyle` 属性则报错。当前可据此自动抓出 `MaterialSwitch`。
8. **阅读设置面板对齐断言**：两个面板的行必须声明 `minHeight`；分组标题必须引用 `TextAppearance.GalIsland.SectionTitle`。
9. **滑块单位注释断言**：两个面板的滑块节点必须带注释写明单位（横排=画笔 px / 行距倍率；竖排=字号 sp / 额外行距 px）。
   ⚠️ **不要**断言两个面板的 `valueFrom`/`valueTo`/`stepSize` 一致——底层单位不同，一致反而是错的（详见 R-4 更正说明）。

---

## 验收缺口

本次为**静态审查**。`docs/galisland-visual-alignment.md:48-60` 记录的设备验收限制仍然存在（Android 16 平板安装被 `INSTALL_FAILED_USER_RESTRICTED` 拒绝），因此：

- P0-1 封面裁切、P0-2 目录阴影的实际观感**未在设备上确认**；
- O-1 卡片按压态、O-2 网格线换肤表现**未实测**；
- 所有结论基于源码，行号已逐条核对，但不等同于像素验收。

建议修复后按 `galisland-visual-alignment.md:54-60` 的截图矩阵补充验收，重点覆盖：列表页与目录页的封面比例、目录抽屉的封面阴影、批量选择态的描边。

补充（R 系列）：
- 阅读设置面板与 `activity_setting.xml` 并排截图，对比分组容器、标题字号、行高节奏；
- 横排 / 竖排两个面板并排截图，对比同一批设置项的排布与间距；
- 开关（`MaterialSwitch`）与相邻标签、滑块并排截图，确认尺寸反差。
