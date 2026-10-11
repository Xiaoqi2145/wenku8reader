# 阅读设置第四轮：对齐 Koodo Reader 的配色分区与滑杆四件套

参考源：`E:\Projects\refer\koodo-reader`（本地源码，`src/containers/panels/settingPanel`、`src/components/readerSettings`、`src/constants/{dropdownList,themeList,settingList}.tsx`）。

承接 [`reader-koodo-alignment.md`](../reader-koodo-alignment.md)（第一~三轮）与 [`reader-p2-polish-plan.md`](./reader-p2-polish-plan.md)（P2 盘点）。
本文只做一件事：**把阅读设置面板本身做到与 Koodo 的 `SettingPanel` 同构**，即「配色分区 + 滑杆四件套 + 自定义色可复用可删除」。
渲染层的 P0-3 排版六项、P1-1 翻页动画、P1-2 进度语义已在第三轮完成，本轮不碰渲染链路。

---

## 一、对照结论：Koodo `SettingPanel` 的五段结构

`containers/panels/settingPanel/component.tsx` 的顺序固定为：

| 段 | 组件 | 我们的现状 |
| --- | --- | --- |
| 视图模式 | `modeControl`：single / double / scroll 三图标，未选中 `opacity:0.4` | ✅ 已有（横竖互切 + 两个按钮） |
| 配色 | `themeList`：**Background color** 一段 + **Text color** 一段，各自「预设 4 色 + 自定义入口 + Clear」 | ⚠️ 有，但混在一个「阅读配色」分组里，且自定义色不可复用 |
| 滑杆 | `sliderList` + `sliderConfigs`：**标题 + 可输入数值 + 当前值 + min/max label + range** 五件套 | ⚠️ 有 label + 数值 + 滑杆，**缺 min/max label** |
| 下拉 | `dropdownList`：Book layout / 字体 / 行高 / 对齐 / 方向 / 翻页动画 … | ⚠️ 我们对齐了「对齐方式 / 翻页动画」，其余不适用（见第五节） |
| 开关 | `settingSwitch` + `readerSettingList` | ✅ 已有（常亮 / 双页 / 按键翻章 / 阅读历史） |
| 更多菜单 | `···` → `Clear all style` | ⚠️ 我们有「恢复默认」按钮，语义等价，但只清配色不清排版 |

### 1.1 Koodo 配色的真正结构（本轮重点）

`components/readerSettings/themeList/component.tsx` 把配色拆成**两条独立的色轴**，每条轴是「预设 + 自定义 + 清除」：

```text
Background color   [⋯自定义入口] [白] [深灰] [米黄] [浅绿] [自定义色…(可删)]
Text color         [⋯自定义入口] [黑] [白]   [棕]   [深绿] [自定义色…(可删)]
```

三个关键行为，我们目前**都没有**：

1. **预设是单轴**。选背景色只改背景，选文字色只改文字。Koodo 的 `handleChangeBgColor` 只在两个特例下联动文字色（选深灰背景 → 白字；选白背景且当前是纯白 → 黑字），其余互不干涉。
   我们目前是 `applyPreset` **整组覆盖**（bg+text 一起换），四条预设是「组合」而不是「色轴」。
2. **自定义色是列表，不是输入框**。`ConfigService.setListConfig(hex, "themeColors")` 把用户调过的色加进列表，之后可作为色块直接点选；色块带 ✕ 可删；预设 4 个之外的才显示删除按钮。
3. **每轴有独立 Clear**（`setReaderConfig("backgroundColor", "")`）—— 清掉覆盖、回落到书籍默认。

我们目前的对应实现是：一个「阅读配色」分组里 4 颗组合预设按钮 + 两个裸 hex 输入框 + 「保存自定义」+「恢复默认」。
`ReadColorRepository.addCustomColor` / `getCustomColors` / `removeCustomColor` **已实现且已单测，但没有任何 UI 读取**——保存进去的自定义色是死数据。

### 1.2 Koodo 滑杆的四件套

`components/readerSettings/sliderList/component.tsx` 的渲染结构（第 84–166 行）：

```text
┌ Font size          [ 17 ]   17 ┐   ← 标题 / 可输入数值 / 当前值
│ 13 ──────────●──────────── 80  │   ← minLabel / range / maxLabel
└────────────────────────────────┘
```

- 输入框 `type=number`，**Enter 与失焦都提交**，越界 `clamp` 回区间（`getClampedValue`）。
- 拖 range 时 `onInput` **实时**写配置 + 实时重渲染；`onMouseUp` 再补一次 `renderBookFunc()`。
- 每根滑杆都带 `minLabel` / `maxLabel` 常量（`constants/dropdownList.tsx` 的 `sliderConfigs`）。

我们第三轮的 `view_reader_slider_row.xml` 只做了「label + 数值框 + Slider」横向一行，**没有 min/max label**，也没有「当前值」独立显示。
且 `setupSliderRow()` 在**两个 Activity 里逐字重复了两份**（`vertical/ReadActivity.kt:644`、`horizontal/ReadActivity.kt:751`），是本轮顺带要消掉的重复。

---

## 二、本轮范围

| 编号 | 项 | 类型 | 静态可验证 |
| --- | --- | --- | --- |
| **R4-1** | 配色分区改为「背景色轴 + 文字色轴」双区 | 面板 + 仓库 | ✅ |
| **R4-2** | 自定义色列表可视化：色块 + 命中态 + ✕ 删除 | 面板 + 新控件 | ✅ |
| **R4-3** | 滑杆行补 min/max label + 当前值，抽成单一绑定器 | 布局 + 公共类 | ✅ |
| **R4-4** | 两块面板内容可滚动（项数已超一屏） | 布局 | ✅ |
| **R4-5** | 清死字段 `presetColorName`，补 strings / zh-rTW | 清理 | ✅ |

**不做**：渲染链路（P0-3 已完成）、下拉项（Book layout / 代码高亮 / 简繁 / 全文翻译 / 词典，见第五节）、单书独立样式（P2-4 另立一轮）、模态浮层跟随阅读配色（观感类，需真机）。

---

## 三、R4-1 配色双轴

### 3.1 数据层

Koodo 的两条轴映射到我们已有的存储：

| Koodo | 我们的字段 |
| --- | --- |
| `backgroundColor` | `ReadColorConfig.bgColorDay` / `bgColorNight` |
| `textColor` | `ReadColorConfig.textColorDay` / `textColorNight` |

我们比 Koodo 多一层「日/夜两套」。**这一层必须保留**——第三轮已把阅读页收敛为白天/黑夜两态（`ReaderThemeMode.converge`），如果改成 Koodo 的单值模型，切日夜就会丢配色。

因此双轴的语义定义为：

- **预设色轴**：一组**共享的**颜色候选，与日夜无关。点背景色轴上的「米黄」→ 把当前模式（日或夜）的 bg 设为米黄；点文字色轴上的「棕」→ 把当前模式的 text 设为棕。
- **自定义色轴**：同样是共享候选列表，存 hex 字符串（不含 alpha）。
- **联动规则照搬 Koodo**：选深灰背景 → 文字色自动转白；选纯白背景且当前文字色是纯白 → 转黑。其余不联动。
- **「恢复默认」**：同时恢复日夜两套（沿用 `restoreDefaults`），语义对齐 Koodo 的 `Clear all style` 但**只清配色**——排版项归各自面板的滑杆，不在这里重置。

### 3.2 新增纯逻辑（可单测）

`data/model/ReaderColorSwatches.kt`：

```kotlin
data class ReaderColorSwatch(val hex: String, val isCustom: Boolean)

object ReaderColorSwatches {
    /** 背景色轴候选：白 / 深灰 / 米黄 / 浅绿（Koodo backgroundList 原值）。 */
    val BACKGROUNDS: List<String> = listOf("ffffff", "2C2F31", "E9D8BC", "C5E7CF")

    /** 文字色轴候选：黑 / 白 / 棕 / 深绿（Koodo textList 原值）。 */
    val TEXTS: List<String> = listOf("000000", "ffffff", "594429", "36503E")

    /** 预设 + 自定义合并成一条轴；自定义排在预设之后，只有自定义可删。 */
    fun axis(presets: List<String>, custom: List<String>): List<ReaderColorSwatch>

    /** 命中判定：大小写不敏感、可带或不带 '#'。 */
    fun indexOf(axis: List<ReaderColorSwatch>, hex: String): Int

    /** Koodo 的联动规则，抽成纯函数以便测试。 */
    fun linkedTextColorFor(bgHex: String, currentTextHex: String): String?
}
```

三处都是纯函数，可直接 JVM 单测（`ReaderColorSwatchesTest`）。

### 3.3 仓库层

`ReadColorRepository` 增：

```kotlin
fun currentBgColor(): String      // 按 readerThemeMode 取日/夜
fun currentTextColor(): String
fun setCurrentBgColor(hex: String)
fun setCurrentTextColor(hex: String)

fun getCustomBgColors(): List<String>
fun getCustomTextColors(): List<String>
fun addCustomBgColor(hex: String): Boolean
fun addCustomTextColor(hex: String): Boolean
fun removeCustomBgColor(hex: String)
fun removeCustomTextColor(hex: String)
```

自定义色存储：把现有 `customColorsJson`（`List<ReaderColorPreset>`，一个组合一个条目）**迁移**为两个 hex 列表 `customBgColorsJson` / `customTextColorsJson`。

> 迁移策略：`ReaderColorPreset` 的 `bgColor` 全部并入背景轴、`textColor` 全部并入文字轴，去重、保序、上限 8。旧键 `custom_colors_json` 保留不删（回滚安全），但代码不再读它。`ReaderColorPreset` / `ReaderColorPresets.PRESETS` 中仅被旧路径使用的部分一并移除。

### 3.4 面板布局

`view_horizontal_read_config.xml` / `view_vertical_read_config.xml` 的「阅读配色」分组改为：

```text
阅读配色
  背景颜色
  [○白] [○深灰] [○米黄] [○浅绿] [○自定义…] [＋]     ← ReaderColorSwatchRow
  文字颜色
  [○黑] [○白] [○棕] [○深绿] [○自定义…] [＋]        ← ReaderColorSwatchRow
  ─────────────────────────────────────────────
  文字色 [#______]  背景色 [#______]                 ← 两个 hex 输入框（保留，作为精确入口）
  [ 保存为自定义 ]   [ 恢复默认 ]
```

---

## 四、R4-2 自定义色控件

新增 `ui/read/common/ReaderColorSwatchRow.kt`：一个横向 `LinearLayout` 子类（或组合式 helper），职责单一——把一条色轴渲染成可点色块行。

- 色块：`ReaderColorSwatch.create()` 已有（圆形 + 描边），直接复用。
- 命中态：当前 hex 对应的色块加 `colorPrimary` 描边（复用 `Widget.GalIsland.Button.IconButton` 的描边语义，不新增 token）。
- 自定义色块带 ✕ 角标：用 `ic_close`，尺寸取 `gi_control_icon` 的一半以下——新增 `gi_color_swatch_size` / `gi_color_swatch_delete_size` 两个 token（`dimens.xml` 里已有先例：`gi_chapter_marker_icon_size`）。
- **只用代码构造**，不写 XML 列表：色块数量随自定义色变化，XML 静态写死会限制上限。但外层容器（`reader_color_swatch_row.xml`）用 XML，保证与面板其余行的内边距一致。

> 校验脚本 `check_galisland_py.py` 禁止布局里出现内联 `textSize/padding/margin` 字面量，也禁止 `Widget.Material3.*` 直接引用。代码构造不受这两条约束，但仍必须走 `gi_*` dimen 与 `Widget.GalIsland.*` style。

---

## 五、R4-3 滑杆四件套

### 5.1 布局改造

`view_reader_slider_row.xml` 从「一行横排」改为「两行式」：

```text
┌ 段间距                    [ 1.5 ] ┐   ← label + 可输入数值框
│ 0 ───────────●────────────── 4   │   ← minLabel / Slider / maxLabel
└──────────────────────────────────┘
```

- 上排：`slider_row_label`（weight 1）+ `slider_row_value`（固定宽、居中、等宽数字）。
- 下排：`slider_row_min`（`TextAppearance.GalIsland.StatusText`）+ `slider_row_slider`（weight 1）+ `slider_row_max`。
- 新增 id：`slider_row_min` / `slider_row_max`。旧 id 全部保留，两个 Activity 的引用不失效。

> 注意：现有代码用 `binding.sliderRowValue.setText(...)`（`EditText.setText`），改用 `TextInputEditText` 后仍可用，但为避免 `TextWatcher` 递归，绑定器统一改用 `setText` 且不加 `addTextChangedListener`。

### 5.2 抽出绑定器

新增 `ui/read/common/ReaderSliderRowBinder.kt`：

```kotlin
object ReaderSliderRowBinder {
    fun bind(
        binding: ViewReaderSliderRowBinding,
        label: String,
        valueFrom: Float,
        valueTo: Float,
        stepSize: Float,
        initialValue: Float,
        onValueChange: (Float) -> Unit
    )
}
```

行为对齐 Koodo：

- `addOnChangeListener` 拖动中实时回调 + 同步数值框（**已是现状，保留**）。
- 数值框失焦提交 + `clamp`（**已是现状，保留**）。
- 新增：数值框 **Enter（IME_ACTION_DONE）也提交**——Koodo 有，我们没有。
- 新增：`min` / `max` 文本自动从 `valueFrom` / `valueTo` 格式化（整数去掉 `.0`），不再逐处手写标签。
- 数值格式化统一走一个纯函数 `formatValue(value: Float): String`（整数不带小数点，`0.1` 步长保留一位），放同文件便于单测。

两个 `ReadActivity` 各删掉自己那份 `setupSliderRow`，改为调 `ReaderSliderRowBinder.bind(...)`。

---

## 六、R4-4 面板可滚动

现状：两个 config 布局的根是 `LinearLayout`（`layout_height=wrap_content`），直接交给 `BottomSheetDialog`。项数增加后，小屏上底部按钮会被挤出屏幕且无法滚动。

改造：根部包一层 `androidx.core.widget.NestedScrollView`，内层保持现有 `LinearLayout`。`BottomSheetDragHandleView` 留在滚动区**外**（否则拖动内容会误触拖拽关闭）。

> 这是本轮的**前置项**——R4-1/R4-3 会让面板变高约 1.5 屏，不先解决滚动，其余改动在小屏上不可用。

---

## 七、R4-5 清理与字符串

1. **删 `presetColorName`**：`ReadColorConfig.presetColorName`、`ReadColorRepository.getPresetColorName/setPresetColorName`、`applyPreset`/`restoreDefaults` 里的赋值。已核实**全项目无读取点**（只有定义与写入），是第三轮双轴化后的遗留。
2. **删 `ReaderColorPreset.PRESETS` 中不再使用的成员**，保留 `DEFAULT_DAY_*` / `DEFAULT_NIGHT_*` / `MAX_CUSTOM_COLORS`（仍被 `restoreDefaults` 与迁移使用）。
3. **strings**：新增 `reader_color_background`、`reader_color_text`、`reader_color_add_custom`、`reader_color_remove_custom`、`reader_color_clear`；`values-zh-rTW` 同步补齐（该文件此前**完全没跟上**第三轮的 P0-2/P0-3 字符串，一并补齐）。
4. **删除死 token**（`verify_res.py` 会报）：`gi_card_padding`、`gi_status_bar_height`、`gi_text_card_title_emphasis`。已在 P2 计划第六节核实零引用。
5. **改 `fragment_guide.xml` 的 `cv_a_select_color_recommend_day` id**（与已删配色页撞名，Kotlin 侧零引用）。

---

## 八、明确不对齐的项

Koodo `dropdownList` / `readerSettingList` 中以下项**本轮不做**，理由与第三轮第四节一致，此处只补新增的：

| 项 | 不做的理由 |
| --- | --- |
| Book layout（赫蹏 / 漢字標準格式 / Tufte / Typebase） | 面向 HTML/EPUB 排版框架；wenku8 是纯文本章节，没有对应渲染层 |
| Code highlighting（30+ 语言） | 轻小说正文无代码块 |
| CJK 副字体 / Default font family | 需要打包字体资源；当前用系统默认，收益/体积比不划算 |
| Line height 下拉（1/1.25/1.5/1.75/2） | 我们已有连续滑杆（横排 1–5 步长 0.1），比枚举更细 |
| Conversion of Chinese（简繁互转） | 文库来源已是简体 |
| Full text translation / 生词释义 / 词典 | 需要翻译服务与词表，属独立产品能力 |
| 自定义 CSS textarea | 无 HTML 渲染层可注入 |
| PDF 专属（margin / scale / brightness / 页边框 / 双页起始 / 保留背景 / 隐藏页眉页脚） | 无 PDF |
| 阅读标尺 / 极速阅读 / 段落模式（三条互斥） | P2-1 阅读标尺在 P2 计划中单列；另两条是 Koodo 特有阅读法 |
| 快速阅读 Bionic / Hyphenation / 允许孤行寡行 | 需要改渲染链路，且收益不明 |
| 面板锁定常驻 / 四向 hover 唤出 | 触屏形态不适用（第三轮已定） |
| 单书独立样式 | P2-4，改造面最宽，另立一轮 |

---

## 九、落地顺序与验收

| 顺序 | 项 | 验收 |
| --- | --- | --- |
| 1 | R4-4 面板可滚动 | 小屏下面板可滚到底部按钮；拖拽把手不误关面板 |
| 2 | R4-3 滑杆四件套 | 6 根滑杆都显示 min/max；数值框 Enter 与失焦都提交并 clamp；拖动实时预览不变 |
| 3 | R4-1 配色双轴 | 点背景色块只改背景；点文字色块只改文字；深灰背景自动转白字 |
| 4 | R4-2 自定义色 | 保存的自定义色出现在轴上；点选生效；✕ 删除；上限 8；重复不新增 |
| 5 | R4-5 清理 | `verify_res.py` 死 token 为 0；两个校验脚本 0 failure |

### 验证方式（无设备，全部静态）

```powershell
$env:JAVA_HOME="D:/SDK/Java/jdk-21"
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug
python build\check_galisland_py.py
python build\verify_res.py
```

新增单测：`ReaderColorSwatchesTest`（轴合并 / 命中判定 / 联动规则）、`ReaderSliderValueFormatTest`（数值格式化与 clamp）。

**像素观感仍不可实测**（无设备，见 `galisland-visual-drift-audit.md`），色块行在窄屏的换行行为属静态审查结论。
