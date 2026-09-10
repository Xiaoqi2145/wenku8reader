# 竖向阅读器重构（阶段四）

## 已完成

阶段四把竖向阅读器拆成以下职责：

- VerticalReaderController：持有 CachedChapter 渲染会话、章节块、稳定锚点映射和追加请求保护。
- VerticalContentRenderer：生成章节标题前缀、标题样式和图片 sink 更新；不计算进度。
- VerticalProgressReporter：从 TextView.Layout 的可见中心获取 UTF-16 偏移，委托 VerticalProgressCalculator 映射到 ReaderPosition，并通过可取消、可 flush 的防抖写入器保存。
- VerticalScrollRestorer：把 ReaderAnchor 转为当前布局目标，等待布局完成，并在字体/行距变化后重新应用锚点。
- VerticalReaderTouchHandler：只负责手势和交互子 View 命中判断，中心点击通过回调交给 UI。

ReadViewModel 现在持有 controller 和连续阅读章节列表，因此 Fragment 重建、主题重建和旋转时可以重新渲染已经追加的章节，而不是只恢复当前章节。加载结果带有 aid/cid/generation/append 信息，Activity 不再处理无作用域的通用加载事件。手动跳章和自动追加通过 ViewModel API 发起。

阅读位置以 ReaderPosition/ReaderAnchor 为主，不再把全局 ScrollView 像素作为新位置。reporter 仅在可计算章节局部坐标时镜像旧 vertical_read_history；旧表仍是没有锚点时的回退，锚点表是当前版本的恢复来源。

## JVM 覆盖

新增：

- VerticalReaderControllerTest（含连续会话上限）
- VerticalReaderCoreTest
- ReaderSessionCoordinatorTest
- VerticalRenderModelTest
- DebouncedPersistenceTest
- VerticalRenderModelFactoryTest

覆盖连续追加、空章节、标题间隔、UTF-16 偏移、重复章节、手动重置、锚点边界、图片索引、滚动目标、触摸区域和防抖 flush/cancel。

## 验证

纯 Kotlin 核心回归测试已通过：13 tests OK（controller/core/render model/debounce，使用本地 kotlinc + JUnit 运行）。完整 Gradle 测试当前受依赖解析阻塞。

完整 Android Gradle 编译仍受环境依赖解析阻塞：:app:dataBindingMergeDependencyArtifactsDebug 无法解析当前 JitPack 依赖（PhotoView、rxhttp、ByRecyclerView、ExpandableTextView、ExpandableRecyclerView、crashx、Channel）。因此本阶段不能声称完整 APK 编译或设备测试通过；恢复网络/依赖缓存后应执行：

    gradlew.bat :app:testDebugUnitTest --no-daemon
    gradlew.bat :app:compileDebugKotlin --no-daemon
    gradlew.bat :app:connectedDebugAndroidTest --no-daemon

设备测试仍需覆盖：长章节性能、多图布局、字体/行距重排、旋转/进程重建、旧历史恢复以及主题切换。
