# 当前阅读器问题清单

以下问题来自阶段一代码审计，后续重构应逐项收敛；阶段三已在下文标注已处理项和遗留边界。

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

- `ReaderRepository` 仍负责下载队列和图片落盘，但内存/磁盘缓存、网络加载、进度存储和预取已拆为独立协作者。
- 预取当前只接受一个正在运行的任务；新的不同章节请求会被丢弃，失败不会影响当前阅读。
- `reader_progress` 已作为锚点真源；旧横向/竖向历史表按方向惰性迁移，只有提供正文时才将百分比转换为字符锚点。
- `cid` 仍是旧 Room 表的主键，当前通过 `aid` 校验避免跨书读取，但复合主键迁移仍待后续阶段。

## 阶段三已建立的保护

- `ChapterCache`、`ChapterLoader`、`ReaderProgressStore` 和 `ChapterPrefetcher` 已从 `ReaderRepository` 拆出；`ReaderRepository` 暂保留为兼容门面。
- 缓存链路按内存、磁盘、网络、过期缓存处理；坏 JSON 和缓存 I/O 异常可回退网络，预取采用单任务、去重和取消安全清理。
- 新 `reader_progress` 作为锚点真源，旧横向/竖向表按方向懒迁移并在提供旧位置时双写；不带正文的迁移不落库，避免把旧百分比覆盖为 0%。
- 下载任务启动时会把遗留的 `RUNNING` 状态恢复为 `QUEUED`；下载编排仍在门面中，暂停/删除竞态和图片文件清理留待后续阶段。
- 由于 `cid` 仍是现有 Room 表主键，缓存和进度读取会校验 `aid`，但数据库复合主键迁移暂不在阶段三范围内。


- 固定 HTML/文本/图片 fixture 与 ReaderBehaviorTest。
- 纯 Kotlin 的 ChapterBlockMapper 和 ChapterNavigator，覆盖空内容、边界、连续追加、锚点映射和空卷跳过。
- 两套 ViewModel 的章节导航已通过 ChapterNavigator 处理，并增加加载代次校验，避免快速跳章时旧响应覆盖新章节。
- ReaderPosition 作为 UI 无关的章节+锚点值对象，为后续会话模型和双向进度迁移提供入口。
