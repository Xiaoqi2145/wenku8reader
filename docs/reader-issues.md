# 当前阅读器问题清单

以下问题来自阶段一代码审计，后续重构应逐项收敛；本清单不改变现有行为。

## 章节与连续阅读

- ReadFragment 仍负责文本布局、滚动监听和几何进度提示；章节块映射已移入带 cid 的 ChapterBlockMapper，但 scrollY 仍保留用于旧历史兼容。
- 手动跳章和上一章/下一章已通过 ChapterNavigator 统一处理，并跳过连续空卷；目录输入也会先做位置校验。
- 自动追加短章节仍有防止链式展开的阈值；ViewModel 已增加加载代次和 ChapterRef 校验，避免旧网络响应覆盖新章节。
- 现有两个阅读 ViewModel 重复实现当前章节、加载、预取和下一章计算。

## 进度恢复

- 竖向阅读仍把 scrollY 和整数百分比一起作为主要进度来源，字体大小、行距、旋转或进程重建后不能保证精确恢复。
- 横向阅读以页码百分比推导字符偏移；分页预处理与原始正文不同，图片页也没有独立的 imageIndex。
- ReaderAnchorMapper 会安全裁剪偏移；显式保存入口和 ReaderRepository 现在会拒绝不匹配当前章节的 anchor.cid。

## 加载与数据

- ReaderRepository 同时负责内存/磁盘缓存、网络加载、下载和预取；内存命中目前仍标记为 DISK_CACHE。
- 预取没有独立的并发上限和取消模型；预取失败与当前章节读取的职责尚未完全隔离。
- 新的 reader_progress 表已经存在，但旧横向/竖向历史表到锚点的迁移仍未完成。

## 本轮已建立的保护

- 固定 HTML/文本/图片 fixture 与 ReaderBehaviorTest。
- 纯 Kotlin 的 ChapterBlockMapper 和 ChapterNavigator，覆盖空内容、边界、连续追加、锚点映射和空卷跳过。
- 两套 ViewModel 的章节导航已通过 ChapterNavigator 处理，并增加加载代次校验，避免快速跳章时旧响应覆盖新章节。
- ReaderPosition 作为 UI 无关的章节+锚点值对象，为后续会话模型和双向进度迁移提供入口。
