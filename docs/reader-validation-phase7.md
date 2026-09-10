# 阅读器灰度验证与性能清单（阶段七）

状态：清单已建立；本工作树尚未完成依赖可用环境下的完整 Gradle、设备和性能验证。

## 回归矩阵

| 场景 | JVM/设备 | 关注点 |
| --- | --- | --- |
| 空、短、超长章节 | JVM + 设备 | 空内容、首屏、追加边界 |
| 图片章节 | JVM fixture + 设备 | 图片索引、图片点击、缓存 |
| 连续追加 2–24 章 | JVM + 设备 | ChapterBlockMapper、会话上限、正确章节锚点 |
| 手动跳章后继续阅读 | 设备 | 旧会话锚点不能污染新会话 |
| 网络失败/缓存命中 | 设备 | STALE_CACHE、预取失败不影响当前章 |
| 旋转/后台/进程重建 | 设备 | ReaderAnchor 恢复、旧记录回退 |
| 字体和行距变化 | 设备 | 布局完成后近似恢复 |
| 横竖屏切换 | 设备 | 新锚点共享，旧页码/scrollY 双写单位不混淆 |
| 同 cid 不同 aid | JVM + migration/device | 不串读、不串删、不覆盖 |
| 快速多次跳章 | 设备 | generation + ChapterRef 丢弃旧响应 |

## 性能预算和观测

- 首章可见时间：不明显超过阶段四基线。
- 滚动采样：相同 globalOffset 不重复提交；ChapterBlockMapper 用换行索引二分查找，避免每次 content.take(offset) 扫描全文。
- 进度写入：防抖 400ms，生命周期结束时 flush，不能阻塞 UI 线程。
- 连续阅读：当前 VerticalReaderController 默认最多保留 24 章，达到上限后停止自动追加；这是在虚拟化 RecyclerView 前的安全止损，不是最终无限阅读方案。
- 内存：记录 TextView 长度、loadedChapters 数量、图片 URL 数量和 heap；不得随阅读无界增长。
- 预取：单任务、可取消、按 (aid,cid) 去重。

性能证据应保存为设备型号/API、构建 variant、章节 fixture、滚动时长、帧率/丢帧、heap 峰值和数据库写入次数，不能用历史 Gradle XML 代替。

## 执行顺序

    .\\gradlew.bat :app:testDebugUnitTest --no-daemon
    .\\gradlew.bat :app:compileDebugKotlin --no-daemon
    .\\gradlew.bat :app:connectedDebugAndroidTest --no-daemon

当 JitPack 依赖可解析且存在 Android emulator 后，再补充：

1. Room MigrationTestHelper 升级矩阵。
2. 固定 fixture 的长章节滚动和图片章节测试。
3. 旋转、进程杀死、缓存离线和横竖切换测试。
4. Android Studio/Profile 或 Perfetto 帧率、内存、I/O 记录。
5. 失败场景的日志和回滚结果。

当前环境验证结论：本轮已完成源码审计和纯 Kotlin 领域源编译检查；完整 Gradle 测试仍在 dataBindingMergeDependencyArtifactsDebug 因 JitPack 依赖解析失败而阻塞，设备验证未执行。
