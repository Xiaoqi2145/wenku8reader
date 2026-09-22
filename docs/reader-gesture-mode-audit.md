# 阅读页手势核查：翻页模式 / 滚动模式

> 核查日期：2026-09-21 ｜ 方式：静态代码审查（**未上真机**）
> 范围：`ui/read/**`、`util/ReaderTouchJudge.kt`、两个阅读 Activity 的布局与引导层、`reader/domain/VerticalTouchPolicy.kt`
> 标注约定：**已核对** = 静态可判定；**待实测** = 机制确定但触发频率需真机确认。

---

## 一、结论摘要

1. **「翻页模式 / 滚动模式」不是运行时开关，而是两个独立 Activity**（`ui/read/horizontal` 与 `ui/read/vertical`），模式切换 = `startActivity` 换页（`horizontal/ReadActivity.kt:570-578`、`vertical/ReadActivity.kt:383-392`）。系统里**没有任何共享的 mode 状态**，因此手势被写了两遍，两套策略各自演化 —— 这是"混在一起"的结构性根因。

2. **横排（翻页）里确实还躺着"滚动式手势"**：`PageView` 与 `PageImage` 各自保留了一整块旧手势实现，其中包含"纵向滑动也翻页"的分支。但这块代码**已经永远不会执行**（被新路径提前 `return true` 短路）。所以它是**代码层面的混用，不是当前可复现的行为**。见 P2-1 / P2-2。

3. **当前真正生效的跨模式混用只有两处**：
   - 竖排（滚动）把"上一章 / 下一章"绑在屏幕左右三分之一（翻页语义进入滚动模式）—— 见 P1-1；
   - 两个模式**共用同一份引导层布局与文案**，文案只描述"翻章"，在横排里与实际行为（翻页）不符 —— 见 P1-2。

4. **另发现一个高风险缺陷**：手势分类只看"按下 → 抬起"的**净位移**，不跟踪过程中是否移动过。旧代码有 `isMoved` 标志，新代码把它丢了。后果是滚动模式下"点一下停住惯性"或"长按后抬手"都会被判成点击，而左右三分区的点击 = **直接跳章**。见 P0-1。

---

## 处置记录（2026-09-21，本次核查之后）

用户决定：**竖排左右点击不再切章，全屏点击统一呼出/收起菜单**（取消三分区）。已落地：

| 改动 | 位置 |
| --- | --- |
| 策略层删掉 `VerticalTapArea` / `tapArea` / `tapAction`，只剩 `shouldToggleReaderBar(isTap, startedOnInteractiveChild)` —— **不再接收任何横向位置入参** | `reader/domain/VerticalTouchPolicy.kt` |
| handler 去掉 `onPreviousChapter` / `onNextChapter` 与不再使用的 `touchTarget` 参数，`onCenterTap` 更名 `onToggleChrome` | `ui/read/vertical/VerticalReaderTouchHandler.kt` |
| Fragment 接线只保留 `onToggleChrome = ::toggleReaderBar` | `ui/read/vertical/ReadFragment.kt:379-388` |
| 策略测试改为真值表（4 组合），删除三分区与交叉一致性用例 | `VerticalReaderCoreTest.kt:60-98` |
| **引导层按模式拆开**：新增 `view_vertical_read_guide.xml` + `reader_tip_body_vertical`，竖排不再复用横排布局 | `res/layout/view_vertical_read_guide.xml`、`activity_vertical_read.xml:12-16` |
| 横排文案修正「翻上一章」→「翻上一页」（那里翻的本来就是页），并补上「左右滑动也可以翻页」 | `res/values/strings.xml:234-239`、`values-zh-rTW/strings.xml:211-216` |

**P1-1 因此关闭。** 副产物：**P0-1 的严重度下降** —— 误判的点击不再跳章、不再改写阅读位置，最坏结果只是菜单闪一下；但"MOVE 不跟踪"本身仍未修（等确认）。

**验证**：`:app:compileDebugKotlin` + `:app:testDebugUnitTest` → BUILD SUCCESSFUL（25 个测试类 / 115 用例 / 0 失败 / 0 跳过），
其中 `VerticalReaderCoreTest.tapTogglesChromeOnlyWhenItIsATapNoInteractiveChildOwns` 覆盖新策略的 4 种组合。
**真机手势未验证**（引导层文案、全屏点击手感需实机确认）。

换章的入口现在只剩显式控件：底栏上一章/下一章、正文首尾的章节按钮、目录抽屉。

---

## 二、手势清单（现状矩阵）

| 模式 | 手势 | 判定实现 | 动作 | 是否生效 |
| --- | --- | --- | --- | --- |
| 翻页（横排） | 单击左 1/3 | `PageView.kt:276-288` → `ReaderTouchJudge.tapArea` | 上一页 | ✅ 生效 |
| 翻页 | 单击中 1/3 | 同上 | 呼出/收起菜单 | ✅ 生效 |
| 翻页 | 单击右 1/3 | 同上 | 下一页 | ✅ 生效 |
| 翻页 | 左右滑动 | `PageView.kt:289-291` → `isHorizontalSwipe` | 上一页 / 下一页 | ✅ 生效（阈值 56dp 且 \|dx\| > \|dy\|×1.25） |
| 翻页 | 纵向 / 斜向滑动 | `PageView.kt:224-235`（旧块） | 按 dx 方向翻页 | ❌ **不可达** |
| 翻页 | 点击图片 | `PageImage.kt:236-242` | 打开原图 / 呼菜单 | ✅ 生效 |
| 翻页 | 音量键 | `horizontal/ReadActivity.kt:243-258` | 翻页（可关） | ✅ 生效（**竖排无此实现**） |
| 滚动（竖排） | 上下拖动 | 由 `NestedScrollView` 自己处理 | 滚动 | ✅ 生效 |
| 滚动 | 单击任意位置 | `VerticalReaderTouchHandler.kt:42-57` → `VerticalTouchPolicy.shouldToggleReaderBar` | 呼出/收起菜单 | ✅ 生效（2026-09-21 已收敛，见「处置记录」） |
| 滚动 | 左右滑动 | — | 无 | — |
| 两模式 | 底栏进度条拖动 | `horizontal:561` / `vertical:336-348` | 跳页 / 跳滚动比例 | ✅ 生效 |

触摸事件归属（**已核对**）：`PageText` 是裸 `View`，不消费触摸，因此横排正文页的 DOWN 会回落到 `PageView.onTouchEvent`；`PageImage` 自己消费全部事件，`PageView` 的 `currentView is PageImage` 分支实际不可达（P2-3）。竖排唯一入口是 `ReadFragment.kt:389-391` 的 `setOnTouchListener`，**全 App 仅此一处** `OnTouchListener`（已 grep 确认），且恒返回 `false`，不抢 `NestedScrollView` 的滚动。

---

## 三、问题清单

### P0-1 手势分类不跟踪 MOVE，滚动会被判成"点击 → 跳章"
**位置**：`ui/read/vertical/VerticalReaderTouchHandler.kt:29-66`、`util/ReaderTouchJudge.kt:20-21`

`onTouch` 只处理 `ACTION_DOWN / POINTER_DOWN / UP / CANCEL`，**没有 `ACTION_MOVE` 分支**；`isTap` 只用 `|Δx|`、`|Δy|` 与 `tapSlop`（`max(scaledTouchSlop, 10dp)`，约 10dp）比较。旧横排实现里的 `isMoved` 标志（`PageView.kt:111-114`、`PageImage.kt:89-92`）在新路径中没有被赋值。

**预测症状（待实测确认）**：
- 惯性滚动中点一下想停住 → 抬手被判定为点击 → 左/右三分之一 = **跳上一章 / 下一章**，并触发 `flushReaderProgress()` + `loadChapter()`（`vertical/ReadActivity.kt:489-530`），阅读位置被写到新章节。
- 手指按住屏幕不动再抬起（阅读时很常见）→ 净位移≈0 → 同样判成点击。
- 横排同样存在，但后果轻（只翻一页）。

**判定依据**：机制由代码直接可判定；"发生频率"未实测。

### P1-1 竖排把"翻章"绑在左右三分区（翻页语义进入滚动模式）
> **已处置（2026-09-21）**：三分区取消，全屏点击统一呼出/收起菜单。见上方「处置记录」。本节保留为当时的核查记录。

**位置**：`reader/domain/VerticalTouchPolicy.kt:12-19,31-37`、`ui/read/vertical/ReadFragment.kt:377-391`

三分区策略本身是有意设计（`docs/reader-koodo-alignment.md` §8.5「第三轮 P2-5」），文档里写的理由是把 Koodo 的四向悬停面板降级为三分区点击。但在连续滚动模式下，"点左/右三分之一 = 换章"与"上下拖动 = 滚动"是两种模式的语义混在同一屏：滚动用户很容易把左右三分区理解为"翻页"，实际却会跳章（配合 P0-1 更明显）。

**需要你确认**：这是要保留的设计，还是要收敛（例如竖排只保留中央呼菜单，翻章交给顶/底按钮与音量键）。

### P1-2 两个模式共用同一份引导层与文案，文案只描述"翻章"
**位置**：`res/layout/view_horizontal_read_guide.xml`、`res/layout/activity_vertical_read.xml:12-16`、`res/values/strings.xml:235`

竖排 Activity **直接 include 了横排的引导布局**（`activity_vertical_read.xml:13-14`），而文案是：

> 点击屏幕左侧三分之一翻上一章，右侧三分之一翻下一章，中央呼出阅读菜单……

- 在**竖排**里这句话是对的（三分区 = 翻章）。
- 在**横排**里就是错的：左右三分之一实际是"翻上一页 / 下一页"，不是"翻上一章 / 下一章"；文案也完全没提"左右滑动翻页"。
- 竖排文案同样没提"上下滑动滚动"。

即：一份为滚动模式写的文案，同时贴在翻页模式的引导层上。**已核对**（布局复用关系可静态确认）。

### P2-1 `PageView` 旧手势整块不可达（含"纵向滑动翻页"）
**位置**：`ui/read/horizontal/PageView.kt:150-151` 与 `153-235`

`onTouchEvent` 第一句是 `if (handleReaderTouchEvent(event)) return true`，而 `handleReaderTouchEvent`（`244-274`）在 **所有分支**都 `return true`（`247`、`249`、`273`）。因此 `153-235` 的整块旧逻辑、`66` 的 `minMove`、`111-114` 的四个手势字段**全部为死代码**。其中 `224-235` 正是"纵向滑动也翻页"的滚动式手势。

### P2-2 `PageImage` 同样有一整块不可达旧手势
**位置**：`ui/read/horizontal/PageImage.kt:108-109`、`111-187`、`195-221`（`220` 恒 `return true`）、字段 `89-92`

与 P2-1 同构：旧块用的是 3/5、2/5 分区 + 5px 点击阈值，新块用的是三分区 + `ReaderTouchJudge`。两套阈值同时存在，只是旧的那套不会跑。

### P2-3 `PageView` 的 `currentView is PageImage` 分支不可达
**位置**：`ui/read/horizontal/PageView.kt:245-248`

`PageImage.onTouchEvent` 恒返回 `true`，DOWN 会被子视图捕获，父级 `PageView.onTouchEvent` 不会再被调用 —— 这个 `dispatchTouchEvent` 转发是防御性残留，与 P2-1 一起说明"两套分发逻辑并存"。

### P2-4 双页模式下点击分区几何与页几何不一致
**位置**：`ui/read/horizontal/PageView.kt:279`（用整幅 `width`）与 `294-300`（双页按 `width/2` 排版）

平板双页时每页宽 = 整幅一半，但三分区仍按整幅切分：中央区（1/3~2/3）会覆盖左页的右半部分，点那里是"呼菜单"而不是"向后翻"。属几何口径不统一，**待实测**确认体感。

### P2-5 音量键翻页只在横排实现
**位置**：`ui/read/horizontal/ReadActivity.kt:243-258`（竖排无对应实现）

跨模式行为不对称：同一本书切到竖排后，音量键翻页开关失效（设置项也只在横排面板里，`view_horizontal_read_config.xml`）。

### P2-6 手势分类器没有单测
`ReaderTouchJudge`（两模式共用的 tap/swipe 判定）无任何测试；竖排策略层有测试（`VerticalReaderCoreTest.kt:61-104`，含 `tapAction` 与 `shouldToggleReaderBar` 的交叉一致性）。也就是说：**真正在跑的那层判定没被测，被测的那层不负责事件**。

---

## 四、建议的收敛方向（确认后再动手）

1. **删掉死代码**：`PageView.kt:153-235` + `66`、`111-114`；`PageImage.kt:111-187` + `89-92`；`PageView.kt:245-248`。让"手势只走 `ReaderTouchJudge`"成为唯一事实。
2. **补 MOVE 跟踪**（修 P0-1）：抽一个纯 Kotlin 的 `ReaderGestureTracker`（记录 `moved`、最大位移、是否超 `scrollSlop`），UP 时若"发生过滚动"则不判 tap。两模式共用，可单测。
3. **明确模式语义表**并写进文档：横排 = 左右三分区翻页；竖排 = ？(待你定 P1-1)。
4. **引导层按模式拆文案**（修 P1-2）：或把 `view_horizontal_read_guide.xml` 改成 `view_reader_guide.xml` + 两份文案资源，或竖排单独建一份。
5. **补测试**：`ReaderGestureTrackerTest`（tap/scroll/fling-stop/多指）、双页分区几何用例。

---

## 五、未实测声明

本文全部结论来自静态代码阅读，**没有在真机上执行过手势**。P0-1、P2-4 的"触发频率 / 体感"必须在真机确认后才能定级；P2-1/P2-2/P2-3 的"不可达"是静态可判定的（短路 `return true` 已逐行核对）。
