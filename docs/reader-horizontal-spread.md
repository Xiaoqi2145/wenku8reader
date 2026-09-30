# 横向阅读器的分页与双页排版契约

范围：`ui/read/horizontal`（`PageView` / `PageText` / `PageImage`）与纯逻辑 `HorizontalPageGeometry`。
本文记录 2026-09-29 修复的两个实机缺陷的根因与由此确立的规则，供后续改动参照。

---

## 一、两个缺陷的根因

### 1.1 横屏文字上溢出（分页预算与实际绘制区不是同一个盒子）

`PageView` 是 `ViewFlipper`，它的**子视图**（`PageText` / `PageImage` / `PageLoading`）才是真正画内容的地方。子视图被布局在 `PageView` 的 padding **之内**，所以子视图高度 = `PageView.height - paddingTop - paddingBottom`。

但分页预算用的是：

```kotlin
// 修复前
(height - readerContentBottomInset - getFooterReservedHeight())
```

**没有减 padding**。`setContentInsets()` 一旦给 `PageView` 设了上下 padding（呼出工具栏时设上 padding、常显状态行设下 padding），分页器就按「未减 padding 的高度」算出「一页能放 N 行」，而实际只能放更少的行。

`PageText` 拿到这 N 行后按内容块垂直居中：

```kotlin
getMarginTop() = (contentHeight - blockHeight) / 2 + mTextSize
```

`blockHeight > contentHeight` 时**结果为负** → 首行被推到页面顶部之外，也就是用户看到的「文字上溢出」。

**第二个叠加原因**：`setContentInsets()` 改了 padding 却**不重新分页**，旋转/分屏也没有重排钩子。于是「呼出工具栏」与「横竖切换」这两个动作必然触发溢出，而不只是偶发。

### 1.2 插图横向独占

`PageView.displayView()` 的插图分支完全不走双页逻辑，`PageImage` 也没有「第二张图」的概念 —— 它是个 `AppCompatImageView`，用 `super.onDraw` 把整张图铺满整个视口。所以双页模式下插图仍独占整幅宽度。

---

## 二、由此确立的规则

### 2.1 分页预算必须与实际绘制区同口径

`HorizontalPageGeometry.contentHeight()` 显式接收并减去 `paddingTop` / `paddingBottom`：

```kotlin
fun contentHeight(
    viewportHeight: Int,
    paddingTop: Int,
    paddingBottom: Int,
    bottomInset: Float,
    footerReserve: Float,
    minimum: Float
): Float
```

`PageView` 传自己的 `paddingTop` / `paddingBottom`，`PageText` 则因为它本身就是子视图（`height` 已经是减完 padding 的），不需要再减一次。**任何改动其中一侧，另一侧必须同步核对。**

### 2.2 几何变化必须重新分页，且按百分比恢复

触发点：`setContentInsets()`（工具栏呼出/收起）、`onSizeChanged()`（分屏改尺寸）。

- 重新分页**必须按 0..1 的百分比**恢复位置，不能按页码 —— 页数变了，同一个页码指向不同正文。
- 重新分页会走 `pageNum` 的 setter，进而触发 `onPageChange` 与重绘。若恢复后的页码与当前相同，setter 不会发通知，此时需**显式** `displayView()`（页码没变但该页的**内容**变了）。
- 两个触发点都可能发生在布局过程中，因此重新分页用 `repaginateSoon()` **post 到下一帧**，并用 `pendingRepaginate` 合并重复请求。

### 2.3 文字页只与文字页配对，插图只与插图配对

一个 `PageText` / `PageImage` 子视图只能渲染一种内容，混排渲染不出来。因此：

- `PageView` 为文字固定创建 2 个子视图（`TEXT_PAGE_CHILDREN`），插图按数量补齐 —— **必须「补足」而不是只建一次**，否则后一章插图比第一章多时会索引到不存在的子视图。
- **奇数长度的段落以「半空的双页」收尾**，而不是把最后一张文字页和第一张插图并排。半空的那一侧是**空白页，不是加载失败**，因此不绘制「加载失败」提示。

### 2.4 插图 spread 的起始页不能用页码奇偶性归一化

文字段从第 1 页起贪心配对，所以文字 spread 都从奇数页号开始。插图段是**在插图段内部**配对的，起始页取决于该段从哪里开始：

> 文字页数为**奇数**时，第一张插图落在**偶数**页号上。若按页码奇偶性归一化，会把这张插图归一到它自己前一页，**这张插图就被藏起来了**。

`spreadStartContaining()` 因此对两段分别计算：

```kotlin
if (page <= textPageCount) {
    if (page % 2 == 0) page - 1 else page          // 文字段：按页码奇偶
} else {
    val imageIndex = page - textPageCount - 1
    textPageCount + 1 + (imageIndex - imageIndex % 2)   // 插图段：按段内下标
}
```

### 2.5 `PageImage` 自己画两半，不调 `super.onDraw`

`PageImage` 仍是 `AppCompatImageView`（Glide 的宿主），但：

- `onDraw` 用 `drawImage(canvas, bitmap, failed, pageLeft, pageWidth)` 分别画左右两半，**不调用 `super.onDraw`** —— 否则 ImageView 会把整张图铺满整个视口。
- `getBitmapRectF(bitmap, pageLeft, pageWidth)` 按**半幅**而非整幅解析 `scaleType`；`FIT_XY` 因为两轴独立缩放，单独提前返回。
- Glide 目标用 `CustomTarget` 而不是 `.into(this)`：两个半页若都 `.into(this)` 会**互相取消**（同一个 view target）。`CustomTarget(0,0)` 会抛异常，尺寸未知时回退 `Target.SIZE_ORIGINAL`。
- 每个半页有**独立的 request generation**，避免旧请求回来后覆盖新页的插图。
- 点按按命中的半页打开**对应**那张图。

---

## 三、测试

`HorizontalPageGeometryTest`（21 例）覆盖：spread 配对、段内归一化、前进步长、后退起点、`contentHeight` 减 padding、百分比恢复。

两条关键断言用「故意改坏」验证过有判别力后还原：

1. 去掉 `contentHeight` 的 padding 减法 → `contentHeightSubtractsTheParentPaddingTheRendererIsLaidOutInside` 失败。
2. 去掉插图段的段内归一化 → `anIllustrationNormalisesWithinTheIllustrationSection` 失败。

---

## 四、未验证

像素观感与真机手势**未在设备上确认**。本文结论来自代码阅读与 JVM 单测；`contentHeight` 的数值取自 `PageView` 自身 padding 与 `gi_reader_content_bottom_inset=36dp`，实际观感需装机确认。
