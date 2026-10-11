建议采用 **“阅读核心渐进式重构”**，周期约 6–8 周；每个阶段都可独立发布、验证和回滚。目标是消除阅读进度、章节拼接和横竖屏逻辑的高耦合，同时保持现有功能可用。

## 总目标

重构完成后：

- 横向、竖向阅读共享统一的章节加载、缓存、预取、导航和进度模型。
- 阅读位置使用稳定的 `ReaderAnchor` 表示，不直接依赖 `ScrollView` 像素位置。
- 章节连续阅读、手动跳章、恢复进度、旋转屏幕行为一致。
- UI 层只负责展示和用户输入，章节拼接与进度计算移到可测试的核心模块。
- 现有数据库和用户数据平滑兼容，无需清除历史记录。

## 阶段一：建立基线和行为保护（第 1 周）

### 工作内容

整理当前阅读器行为，建立测试样本：

- 空章节、短章节、超长章节。
- 含图片章节。
- 连续追加 2–5 个章节。
- 手动跳转后继续自动加载。
- 网络失败但有缓存。
- 进度恢复到章节开头、中间和结尾。
- 屏幕旋转、后台恢复、进程重建。
- 横向与竖向模式切换。

补充纯 Kotlin 单元测试，重点覆盖：

- `ReaderAnchorMapper`
- 章节索引和上一章/下一章计算
- 章节边界转换
- 百分比与字符偏移互转
- 空内容和异常偏移处理

### 产出

- `ReaderBehaviorTest`
- 当前问题清单
- 阅读器行为基线文档
- 一组固定的 HTML/文本/图片 fixture

### 验收标准

基线测试全部通过；任何后续改动都必须先通过这些测试。

---

## 阶段二：统一阅读领域模型（第 2 周）

当前已有 `ChapterRef`、`CachedChapter`、`ReaderAnchor`，但状态仍分散在两个 ViewModel 和 Fragment 中。

### 新增模块

建议新增：

```text
reader/
  domain/
    ReaderSession.kt
    ReaderPosition.kt
    ChapterNavigator.kt
    ChapterContent.kt
    ReaderProgressCalculator.kt
    ChapterBlockMapper.kt
```

### 核心职责

`ReaderSession`

- 当前小说
- 当前章节
- 已加载章节列表
- 当前阅读位置
- 加载状态
- 是否允许继续加载

`ReaderPosition`

统一表达阅读位置：

```kotlin
data class ReaderPosition(
    val chapter: ChapterRef,
    val anchor: ReaderAnchor
)
```

`ChapterNavigator`

统一处理：

- 上一章
- 下一章
- 跨卷切换
- 空卷跳过
- 边界判断

`ChapterBlockMapper`

负责连续阅读中的映射：

- 单章内容在拼接文本中的起止偏移
- 全局字符偏移转章节局部偏移
- 局部偏移转章节和百分比
- 章节追加后的锚点修正

### 验收标准

领域模块不依赖 Android UI；可通过 JVM 单元测试验证所有章节导航和偏移计算。

---

## 阶段三：重构加载、缓存和预取链路（第 3 周）

保留现有 `ReaderRepository`，但收敛职责，避免 ViewModel 直接管理过多加载细节。

### 建议拆分

```text
ReaderRepository
 ├── ChapterLoader
 ├── ChapterCache
 ├── ReaderProgressStore
 └── ChapterPrefetcher
```

### 规则

`ChapterLoader`

- 统一内存缓存、磁盘缓存、网络加载顺序。
- 明确返回 `ContentSource`。
- 缓存内容损坏时自动降级到网络。

`ChapterCache`

- 只负责缓存读写。
- 不参与 UI 状态和章节导航。

`ReaderProgressStore`

- 统一读写横向、竖向进度。
- 新模型与旧数据库字段双向转换。
- 首次读取旧记录时自动转换为 `ReaderAnchor`。

`ChapterPrefetcher`

- 只预取下一章。
- 限制并发数量。
- 避免连续短章节导致整本书一次性加载。

### 验收标准

- 网络失败时可读取缓存。
- 预取失败不影响当前阅读。
- 下载任务与正常阅读加载互不干扰。
- 所有数据层测试通过。

---

## 阶段四：重构竖向阅读核心（第 4–5 周）

这是优先级最高的重构区域。当前 `ReadFragment` 同时负责文本拼接、章节块映射、滚动监听和进度计算，应拆开。

### 重构后的结构

```text
vertical/
  VerticalReaderController.kt
  VerticalContentRenderer.kt
  VerticalProgressReporter.kt
  VerticalScrollRestorer.kt
  VerticalReaderTouchHandler.kt
  ReadFragment.kt
```

### 各模块职责

`VerticalReaderController`

- 接收加载结果。
- 控制追加章节。
- 管理当前阅读会话。

`VerticalContentRenderer`

- 生成章节标题。
- 生成 `SpannableString`.
- 更新图片列表。
- 不计算进度。

`VerticalProgressReporter`

- 根据可见区域计算字符偏移。
- 通过 `ChapterBlockMapper` 转换为章节锚点。
- 定时或滚动节流后保存进度。

`VerticalScrollRestorer`

- 根据 `ReaderAnchor` 恢复滚动。
- 处理布局尚未完成的情况。
- 处理字体大小、行距变化后的近似恢复。

`VerticalReaderTouchHandler`

- 只处理点击区域和手势判断。
- 不直接操作 ViewModel 状态。

### 关键改动

移除对以下状态的直接依赖：

- `activeChapterStartOffset`
- Fragment 内部自行维护的 `chapterBlocks`
- 以 `scrollY` 作为持久化阅读位置
- Fragment 直接决定章节索引

这些状态应由 controller 或 domain mapper 管理。

### 验收标准

- 连续章节追加后进度仍对应正确章节。
- 手动跳章后旧章节锚点不会污染新章节。
- 改变字体大小或行距后能够恢复到相近文本位置。
- 旋转屏幕后阅读位置误差控制在一个屏幕以内。
- 长章节滚动性能无明显下降。

---

## 阶段五：收敛横向与竖向 ViewModel（第 6 周）

两个 `ReadViewModel` 当前存在明显重复逻辑，应抽取公共基类或组合式组件。

推荐组合，而不是继承：

```kotlin
class ReaderSessionCoordinator(
    private val repository: ReaderRepository,
    private val navigator: ChapterNavigator,
    private val progressStore: ReaderProgressStore
)
```

横向和竖向 ViewModel 共同使用：

- 初始化小说
- 当前章节管理
- 加载与刷新
- 缓存读取
- 预取
- 下载
- 章节导航
- 锚点保存和恢复

各自保留：

- 横向分页状态
- 竖向滚动状态
- 对应 UI 配置
- 手势和渲染策略

### 验收标准

- 公共逻辑只有一份实现。
- 横向和竖向行为差异只存在于展示层。
- 新增阅读功能时不需要修改两套相同代码。

---

## 阶段六：数据库兼容和数据迁移（第 7 周）

### 保持兼容

暂时不删除现有表和字段：

- 旧位置字段继续保留。
- 新增锚点字段或新增进度表。
- 通过 migration 逐步迁移。

建议新增字段：

```text
anchorCharOffset
anchorParagraphIndex
normalizedPercent
imageIndex
updatedAt
```

### 迁移策略

1. 新版本读取新字段。
2. 新字段为空时读取旧字段。
3. 用户产生新进度后写入新字段。
4. 运行一段时间后再评估是否删除旧字段。

### 验收标准

- 升级安装不丢失阅读记录。
- 降级或旧数据读取不会崩溃。
- 数据库 migration 测试通过。

---

## 阶段七：灰度验证和清理（第 8 周）

### 验证方式

使用固定测试书籍进行：

- 章节切换测试
- 连续阅读测试
- 进程杀死恢复测试
- 网络断开测试
- 缓存命中测试
- 旋转屏幕测试
- 字体设置变化测试
- 多次快速跳章测试

### 性能指标

- 首章可见时间不明显增加。
- 追加章节不产生明显卡顿。
- 内存不会随连续阅读无限增长。
- 单次进度保存不能阻塞 UI 线程。
- 预取任务可取消。

### 清理内容

- 删除 Fragment 中重复的进度计算。
- 删除旧的临时章节偏移变量。
- 删除横竖 ViewModel 重复方法。
- 补充架构说明和迁移说明。
- 更新 README 中关于项目状态和构建方式的说明。

## 推荐提交拆分

每个提交保持可编译、可回滚：

1. `test: add reader behavior baseline`
2. `refactor: extract chapter navigation domain`
3. `refactor: extract chapter block mapper`
4. `refactor: unify reader progress model`
5. `refactor: split chapter loading and cache`
6. `refactor: migrate vertical reader progress`
7. `refactor: extract vertical renderer and controller`
8. `refactor: share reader session coordinator`
9. `feat: add progress database migration`
10. `test: add reader regression suite`
11. `chore: remove obsolete reader state`

## 风险控制

主要风险是进度恢复行为变化和连续阅读边界错误，因此每一步都保留旧逻辑作为对照，先做双写或双算：

- 新旧进度同时计算，日志比较结果。
- 新旧锚点同时保存一段时间。
- 发现偏差时优先回退到旧恢复路径。
- 数据库迁移与 UI 重构分开提交。
- 不在同一个提交中同时修改网络、数据库和阅读 UI。

## 最终判断

执行顺序应是：

```text
行为测试
  → 章节与锚点领域层
  → 加载缓存链路
  → 竖向阅读重构
  → 横竖屏逻辑复用
  → 数据迁移
  → 灰度验证与清理
```

第一阶段最值得立刻开始的是：**先建立阅读行为测试，再抽取 `ChapterBlockMapper` 和 `ChapterNavigator`。**这两个模块风险低、收益高，也能为后续重构提供稳定反馈。