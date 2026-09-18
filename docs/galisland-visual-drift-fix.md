# GalIsland 视觉偏移修复记录

修复日期：2026-09-18
依据：`docs/galisland-visual-drift-audit.md`（同批审查产出）
验证：`./gradlew :app:assembleDebug` **BUILD SUCCESSFUL**（JDK 21，4m11s）

---

## 修复总览

| 编号 | 问题 | 状态 | 视觉影响 |
| --- | --- | --- | --- |
| P0-1 | 封面比例：列表/目录 7:10 → 4:5 | ✅ 已修 | **有**（封面高度 160→140dp） |
| P0-2 | 目录封面 elevation 3dp | ✅ 已修 | **有**（阴影消失） |
| P1-3 | `gi_card_stroke` 死 token | ✅ 已修 | **有**（选中卡片出现主色描边） |
| P1-4 | 跨语义域复用 token | ✅ 已修 | 无（数值保持不变） |
| P1-5 | `gi_metric_*` 伪语义 token | ✅ 已修 | 无（保持原数值，改为语义 token） |
| P2-6 | styles 层硬编码尺寸 | ✅ 已修 | 无（数值保持不变） |
| P2-7 | 卡片样式继承链冗余 | ✅ 已修 | 无 |
| P2-8 | `values-night/themes.xml` 冗余 | ✅ 已修 | 无（删除等价重复文件） |
| P2-9 | `MaterialSwitch` 未映射 M3 默认样式 | ✅ 已修 | **有**（开关触控目标 48dp） |
| R-1 | 阅读设置面板无分组容器 | ✅ 已修 | **有**（新增纸片描边分组） |
| R-2 | 分组标题未用 SectionTitle | ✅ 已修 | **有**（字号 14→13sp） |
| R-3 | 行高与间距手法不同 | ✅ 已修 | **有**（行高统一 56dp） |
| R-4 | 横竖排面板差异 | ✅ 已更正 + 部分修 | 无 |
| R-5 | 同面板标题间距不一致 | ✅ 已修 | **有**（14→12dp） |
| R-6 | 阅读页工具栏写法分叉 | ✅ 已修 | 无（结果等价） |

---

## P0-1 统一封面比例为 4:5

`values/dimens.xml`：

```xml
<dimen name="gi_cover_width">112dp</dimen>
<!-- 小说封面 4:5（112 x 140）。规范第 2 节：小说封面比例 4:5。 -->
<dimen name="gi_cover_height">140dp</dimen>
```

`gi_cover_height` 由 160dp（7:10）改为 140dp（4:5）。生效范围：

- `layout/item_novel_cover_liner.xml`（经 `galisland_novel_list_cover_width/height` 间接引用）
- `layout/fragment_read_chapter_catalog.xml:74`

**连带处理**：`gi_cover_height` 原被 `fragment_logging_in.xml:12`、`fragment_login_error.xml:13` 借作登录页 logo 高度。为避免改封面比例时误改 logo，先抽出独立语义 token：

```xml
<dimen name="gi_brand_logo_height">160dp</dimen>
```

两处已改为引用该 token，logo 高度保持 160dp 不变。

---

## P0-2 目录封面 elevation 归零

`layout/fragment_read_chapter_catalog.xml` 的封面卡片原有 4 处属性与样式重复或违规，一并清理：

```xml
<!-- 外观全部来自 Widget.GalIsland.CardView.Novel.Cover：
     8dp 圆角、0dp 描边、0dp elevation（规范第 2 节：卡片 elevation 0dp）。 -->
<com.google.android.material.card.MaterialCardView
  style="@style/Widget.GalIsland.CardView.Novel.Cover"
  android:layout_width="@dimen/gi_catalog_cover_width"
  android:layout_height="@dimen/gi_cover_height"
  android:contentDescription="@string/cover"
  android:foreground="?attr/selectableItemBackground"
  tools:ignore="ContentDescription">
```

删除的 3 项：`app:cardCornerRadius`（样式已有）、`app:cardElevation`（3dp，违规）、`app:strokeWidth`（0dp，下沉到样式）。

`values/galisland_styles.xml` 的 `Widget.GalIsland.CardView.Novel.Cover` 补入：

```xml
<!-- 封面容器无描边（模板 .card-cover）；elevation 继承父样式 0dp。 -->
<item name="strokeWidth">0dp</item>
```

`values/dimens.xml` 删除已无引用的 `gi_catalog_cover_elevation`。

**验证**：全项目非 0 elevation 已清零。

---

## P1-3 接入 `gi_card_stroke`

`values/galisland_cards.xml`：

```xml
<!-- 外观全部继承 Widget.GalIsland.CardView.Novel：
     8dp 圆角 / 1dp 描边 / 0dp elevation / colorSurface 底色，此处不再重复声明。
     strokeColor 接入 gi_card_stroke，使选中（state_selected）与聚焦态显示主色描边，
     兑现 galisland-visual-alignment.md 中「右上角选择角标与强调描边」的承诺。 -->
<style name="Widget.GalIsland.NovelCards.Card" parent="Widget.GalIsland.CardView.Novel">
    <item name="strokeColor">@color/gi_card_stroke</item>
    <item name="android:clickable">true</item>
    <item name="android:focusable">true</item>
</style>
```

原先 `gi_card_stroke.xml` 定义后引用数为 0；现已接入，卡片选中态会出现 `colorPrimary` 描边。

---

## P1-4 消除跨语义域 token 复用

### 规模修正

审查报告初版只报了 7 处。实际全面扫描后发现 **`gi_control_icon`(20dp) 被系统性当作通用间距**，共 **49 处**（margin 37 + padding 12），跨 16 个布局文件。另加：

| 误用 token | 处数 | 说明 |
| --- | --- | --- |
| `gi_control_icon`(20dp) | 49 | 被当 20dp 通用间距 |
| `gi_status_icon`(14dp) | 5 | 被当 14dp 间距 |
| `gi_grid_unit`(40dp) | 1 | 被当 40dp 间距 |
| `gi_radius_tag`(6dp) | 4 | 被当 6dp 间距 |
| `gi_control_icon`(20dp) | 1 | 被当左边距 |
| `gi_cover_width`(112dp) | 1 | 被当缩略图高度 |
| `gi_cover_height`(160dp) | 2 | 被当 logo 高度 |

### 修复方式：保持数值，改用语义 token

新增间距 token（`values/dimens.xml`）：

```xml
<!-- 模板中存在、但不落在 4/8/12/16/24 梯级上的精细间距值。
     独立命名，避免被误当作圆角（gi_radius_*）或图标尺寸（gi_control_icon）复用。 -->
<dimen name="gi_space_gap">6dp</dimen>
<dimen name="gi_space_gap_lg">14dp</dimen>
<dimen name="gi_space_gap_xl">20dp</dimen>
<dimen name="gi_space_gap_xxl">40dp</dimen>
```

新增尺寸 token：

```xml
<!-- 历史记录缩略图（90 x 112 ≈ 4:5）与登录页品牌图高度，语义独立于封面。 -->
<dimen name="gi_thumbnail_width">90dp</dimen>
<dimen name="gi_thumbnail_height">112dp</dimen>
<dimen name="gi_brand_logo_height">160dp</dimen>
```

**选择"保持数值"而非"向梯级收敛"的原因**：设备安装受限（`INSTALL_FAILED_USER_RESTRICTED`），像素观感无法验收。改值会引入无法验证的视觉变化，因此本次只做语义澄清，数值一律不变。

替换方式：按属性类型精确匹配，只替换 `layout_margin*` / `padding*` 属性，尺寸类用法（`layout_width/height`、`iconSize`）不受影响。`gi_page_inset`(16dp) 的 66 处引用属正当用法（语义即页面内边距），未改动。

**验证**：尺寸类 token 被当间距用的情况已清零。

---

## P2-6 styles 层硬编码回填 token

`values/galisland_styles.xml` 原有 30+ 处字面量尺寸。回填后：

| 字面量 | 处数 | 替换为 |
| --- | --- | --- |
| 14sp | 1 | `gi_text_card_title` |
| 13sp | 2 | `gi_text_section` |
| 12sp | 3 | `gi_text_body` |
| 11sp | 1 | `gi_text_meta` |
| 10sp | 2 | `gi_text_micro`（新增） |
| 16sp | 1 | `gi_text_empty_title`（新增） |
| 1dp（strokeWidth 系） | 6 | `gi_stroke` |
| 4dp（insetTop/Bottom） | 2 | `gi_space_xs` |
| Slider / Tab / 导航指示器 | 6 | 新增 `gi_slider_track_height` / `gi_slider_thumb_radius` / `gi_slider_halo_radius` / `gi_tab_indicator_height` / `gi_nav_indicator_width` / `gi_nav_indicator_height` |

新增字级 token：

```xml
<dimen name="gi_text_micro">10sp</dimen>
...
<dimen name="gi_text_empty_title">16sp</dimen>
```

**验证**：`galisland_styles.xml` 中除 `0dp`（语义为"无描边/无高程"）外，已无字面量尺寸。

这同时消除了报告指出的同文件矛盾——`TextAppearance.GalIsland.CardTitle`(原写死 14sp) 与 `TextAppearance.GalIsland.NovelTitle`(用 `@dimen/gi_text_card_title`) 现已统一走 token。

---

## P2-7 清理卡片样式继承链冗余

`Widget.GalIsland.NovelCards.Card` 原先重复声明了父样式已有的 4 项（`cardBackgroundColor` / `cardElevation` / `strokeWidth` / `strokeColor`）。现已删除，只保留差异化属性（`strokeColor` 接入 `gi_card_stroke` + 交互语义）。

---

## P2-8 删除 `values-night/themes.xml`

先做逐项等价性验证：提取两个文件的 Base 主题属性名集合做 diff，结果除新增的 `materialSwitchStyle` 外**完全一致**（25 项全等）。

该文件仅为 `Base.Theme.HikariNovel` 的重复定义，昼夜差异实际由 `values-night/colors_*.xml` 的颜色变体承担。已删除。

**配套修改**：`scripts/check-galisland.ps1` 原先会读取 `values-night/themes.xml` 校验 Chrome token，文件删除后 `Get-Content` 会抛错导致脚本失败。已更新该断言，并加注释说明 Base 主题现只在 `values/themes.xml` 维护。

---

## P2-9 新增 `Widget.GalIsland.Switch` 并注册

`values/galisland_styles.xml`：

```xml
<!-- 规范第 3 节禁止使用未映射的 Material3 默认组件样式；此前 materialSwitchStyle 缺位，
     10 处 MaterialSwitch 全部落到 M3 默认样式。开关本体尺寸沿用 M3（不缩小，保证手势可达性），
     只把触控目标对齐 GalIsland 控件高度。 -->
<style name="Widget.GalIsland.Switch" parent="Widget.Material3.CompoundButton.MaterialSwitch">
  <item name="android:minHeight">@dimen/gi_control_height</item>
</style>
```

注册位置：`values/themes.xml` 的 `Base.Theme.HikariNovel` 与 `Theme.HikariNovel.GalIsland` 各一条 `materialSwitchStyle`。

覆盖原先 10 处未映射的 `MaterialSwitch`（`activity_setting.xml` 1 + 横排面板 6 + 竖排面板 3）。**未缩小开关本体**——只对齐触控目标，避免破坏手势可达性。

---

## R-1 / R-2 / R-3 阅读设置面板对齐主视觉

两个面板（`layout/view_horizontal_read_config.xml`、`layout/view_vertical_read_config.xml`）整体重写，采用与 `activity_setting.xml` 相同的分组语言：

| 维度 | 修复前 | 修复后 |
| --- | --- | --- |
| 分组容器 | 无 | `@drawable/gi_secondary_panel`（透明底 + 1dp `colorOutlineVariant` 描边 + 8dp 圆角） |
| 分组标题 | 纯内联，无 `textAppearance` → 默认 14sp | `TextAppearance.GalIsland.SectionTitle`（13sp bold）+ `?attr/colorPrimary` |
| 行高 | 无 `minHeight`，随控件浮动 | `gi_secondary_row_height`(56dp) |
| 行内边距 | 行间 margin + 左右 margin | 行内 `padding`：左右 `gi_space_lg`(16dp)、上下 `gi_space_sm`(8dp) |
| 行对齐 | `gravity="center"` | `gravity="center_vertical"` |

分组划分：横排 = 「显示设置」（字号/行距/进度文字）+「分页设置」（7 项）；竖排 = 「显示设置」单组（竖排无分页概念）。

**所有控件 id 与滑块参数保持不变**（`stepSize` / `valueFrom` / `valueTo` 逐项核对未动）。

---

## R-4 更正与处理

**审查结论更正**：初版把两个面板的滑块参数差异判为「分叉」，这是**误判**。查证底层单位后确认差异是有意设计：

- 横排字号 = 画笔 px（`PageView.textSize`，默认 55，范围 30–100）；竖排行距 = 额外行距 px（`TextView.setLineSpacing(add, mult)`，范围 1–50）
- 横排行距 = 倍数（`PageText` 以 `textSize * rowSpace` 排版，默认 1.5，范围 1–5 步长 0.1）；竖排字号 = sp（默认 16，范围 1–50）

**范围与步长必须不同，强行统一会破坏渲染。** 报告已更正，并移除了原「跨面板分叉检测」这条基于误判的检查建议。

本轮实际处理：

1. **统一按钮 id**：`b_v_h_read_config_color` → `b_v_h_read_config_change_color`（与竖排 `b_v_v_read_config_change_color` 对齐），同步修改 `ui/read/horizontal/ReadActivity.kt:450` 的绑定引用。
2. **补单位注释**：两个面板头部均加注释说明各滑块的底层单位，防止后续维护者重蹈误判。

---

## R-5 统一分组标题间距

`view_horizontal_read_config.xml` 原「分页设置」标题用 `gi_status_icon`(14dp) 作 `paddingTop`（语义为状态行图标尺寸），而「显示设置」用 `gi_space_md`(12dp)。已统一为 `gi_space_md`。

重写面板后，两个标题均改用 `layout_margin="@dimen/gi_space_inset"` + `paddingLeft`，与 `activity_setting.xml` 的标题写法一致。

---

## R-6 阅读页工具栏统一写法

`layout/activity_horizontal_read.xml`、`layout/activity_vertical_read.xml`：

```xml
<!-- 与主视觉二级页同一工具栏样式（Secondary.Toolbar = Toolbar + elevation 0dp）；
     透明底、minHeight、elevation 均由样式提供，不在布局重复声明。 -->
<com.google.android.material.appbar.MaterialToolbar
  android:id="@+id/tb_a_h_read"
  style="@style/Widget.GalIsland.Secondary.Toolbar"
  android:layout_width="match_parent"
  android:layout_height="wrap_content"
  tools:title="title"
  app:menu="@menu/menu_act_v_h_read" />
```

`Widget.GalIsland.Toolbar` → `Widget.GalIsland.Secondary.Toolbar`，删除布局中样式已声明的 `android:background`(transparent)、`android:minHeight`、`android:elevation`。

---

## P1-5 收敛 `gi_metric_*` 伪语义 token

本轮按用途逐处分类，保持原有数值不变，新增语义 token 覆盖：页面间距、品牌/封面尺寸、章节行高、图标尺寸、渐隐长度、空状态图标和阅读字级。

- 所有布局与资源中的 `@dimen/gi_metric_*` 引用已清零。
- `values/galisland_layout_metrics.xml` 已删除。
- `check-galisland.ps1` 已增加断言，禁止旧 token 或同前缀资源回流。

---

## 验证结果

### 静态验证

| 项目 | 结果 |
| --- | --- |
| XML 语法（211 个资源文件） | 0 错误 |
| 布局层 `gi_`/`galisland_` 资源引用 | 0 缺失 |
| Kotlin 层 `R.dimen/color` 引用 | 0 缺失 |
| `check-galisland.ps1` 全部断言（Python 等价复现） | **0 失败** |
| 尺寸类 token 被当间距用 | 已清零 |
| `gi_metric_*` 旧 token 引用 | 已清零 |
| 非 0 elevation | 已清零 |

### 构建验证

```
./gradlew :app:assembleDebug -Pksp.incremental=false --no-daemon
BUILD SUCCESSFUL in 4m 11s
44 actionable tasks: 17 executed, 27 up-to-date
```

**环境提示**：本次构建需显式指定 `JAVA_HOME=D:/SDK/Java/jdk-21`。当前系统默认 `java` 为 Zulu JDK 25，Gradle 8.13 不支持，直接运行会以 `What went wrong: 25.0.4` 失败。建议在 `gradle.properties` 中固定 `org.gradle.java.home`，或在构建脚本里设置。

### 待清理

`build/verify_res.py`、`build/check_galisland_py.py` 为本次验证用的临时脚本（位于 `build/`，通常不入版本控制），可删除。

---

## 需设备验收的视觉变化清单

以下改动会改变像素观感，但受 `INSTALL_FAILED_USER_RESTRICTED` 限制**未在设备上确认**：

| 改动 | 预期效果 |
| --- | --- |
| 列表卡片 / 目录封面高度 160→140dp | 封面按 4:5 呈现，纵向裁切减少约 14% |
| 目录封面 elevation 3dp→0 | 封面阴影消失，与其余卡片一致 |
| 卡片 `strokeColor` 接入 `gi_card_stroke` | 选中/聚焦卡片出现主色描边 |
| `MaterialSwitch` 触控目标 48dp | 开关行更高，手势更易命中 |
| 阅读设置面板新增分组描边 | 出现两个 8dp 圆角描边框（横排）/ 一个（竖排） |
| 阅读面板标题 14→13sp | 与主视觉设置页标题一致 |
| 阅读面板行高统一 56dp | 行距更疏，节奏与设置页一致 |
| 「分页设置」标题间距 14→12dp | 与「显示设置」对齐 |

建议按 `docs/galisland-visual-alignment.md` 的截图矩阵补充验收，重点覆盖：列表页与目录页封面比例、批量选择态描边、阅读设置面板与 `activity_setting.xml` 的并排对比。
