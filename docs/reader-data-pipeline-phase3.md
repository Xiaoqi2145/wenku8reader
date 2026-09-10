# 阅读数据链路阶段三

本阶段将阅读数据链路从 ReaderRepository 中拆出，同时保留原门面 API，便于横向、竖向阅读器和下载页面渐进迁移。阶段三实现已完成源码改造；完整 Gradle 验证仍受当前环境外部依赖解析限制。

## 结构

ReaderRepository（兼容门面、下载编排）
 ├── ChapterLoader
 │    └── ChapterRemoteSource -> Wenku8Repository
 ├── ChapterCache
 │    ├── 内存 LRU（8 项）
 │    └── ReaderDao.chapter_cache
 ├── ReaderProgressStore
 │    ├── ReaderDao.reader_progress（新锚点真源）
 │    └── 旧 vertical/horizontal history（惰性迁移、可选双写）
 └── ChapterPrefetcher（单任务、cid 去重）

## 加载与缓存规则

1. 普通加载先查内存，再查磁盘缓存。
2. forceNetwork=true 跳过缓存并请求网络。
3. 网络成功后写入缓存；缓存写失败不影响当前网络响应。
4. 网络失败且有缓存时返回 STALE_CACHE。
5. 缓存 JSON 或缓存 I/O 异常按 cache miss 处理，继续尝试网络。
6. 缓存命中会检查 aid，避免旧 cid-only 表将内容串到另一部书。

## 进度兼容

- 新位置保存为 paragraphIndex、charOffset、normalizedPercent 和 imageIndex。
- 旧表的百分比在读取到正文后转换为字符锚点。
- 不带正文的 getProgress(cid) 只返回临时百分比锚点，不写入 reader_progress，避免 charOffset=0 后续把百分比重算为 0。
- 保存时只有调用者提供了真实的旧位置（竖向 scrollY 或横向 page）才镜像写入旧表；anchor-only 保存不会把字符偏移写入旧位置字段。
- 新旧表不是同一个 Room 事务；新 reader_progress 是当前真源，旧镜像失败不会阻塞阅读。
- 阶段六已加入 reader_data 与旧历史表的非破坏性 (aid, cid) 复合主键迁移；旧 cid-only DAO/API 只作为 deprecated 兼容入口，不能用于新的阅读路径。

## 预取与下载边界

- ChapterPrefetcher 只执行调用方选定的一个预取任务；同 (aid,cid) 重复请求会被忽略，不同章节在已有任务运行时也不会排队。
- 预取失败是 best-effort，取消时保证释放去重状态。
- 下载队列暂留在 ReaderRepository，与正常阅读加载不共用下载锁；应用启动会把遗留 RUNNING 任务重置为 QUEUED。
- 阶段三尚未解决下载暂停/删除与正在运行协程之间的竞态，也未删除图片文件目录。

## 测试和验证

新增纯 JVM 测试覆盖：

- cache hit、force network、stale cache、网络响应不受 cache 写失败影响；
- 预取失败、同 cid 并发去重、不同 cid 丢弃、取消后可重试；
- legacy 百分比转换、空内容占位、字段归一化和正文变化后的锚点裁剪。

完整 Gradle 测试命令：

    .\\gradlew.bat :app:testDebugUnitTest --no-daemon

当前环境在 dataBindingMergeDependencyArtifactsDebug 阶段无法解析 JitPack 依赖，因此尚未取得完整 Gradle 编译结论；这不是阶段三源码测试失败。
