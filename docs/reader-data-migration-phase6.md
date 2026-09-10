# 阅读数据迁移契约（阶段六）

状态：源码迁移已加入工作树，Room/设备验证仍待依赖可用环境。本文记录从 reader_data v2、旧横向/竖向历史表升级到当前锚点模型的兼容契约。

## 数据模型

| 数据 | 旧版本 | 当前版本 |
| --- | --- | --- |
| 阅读位置 | horizontal/vertical history 的 location、progress_percent | reader_progress 的 paragraphIndex、charOffset、normalizedPercent、imageIndex、updatedAt |
| 章节缓存 | chapter_cache 以 cid 为主键 | v3 以 (aid, cid) 为主键 |
| 阅读进度 | reader_progress 以 cid 为主键 | v3 以 (aid, cid) 为主键 |
| 下载任务 | download_task 以 cid 为主键 | v3 以 (aid, cid) 为主键 |
| 旧历史表 | cid 为主键 | 各自 v2 迁移到 (aid, cid) |

ReaderDatabaseMigrations.READER_1_2 保留已有数据并创建 download_book；READER_2_3 重建缓存、进度和下载任务表，使用 INSERT OR IGNORE 复制旧行。横向、竖向历史数据库分别使用同一组非破坏性 1→2 重建策略。当前没有 destructive fallback。

## 读取与惰性迁移

1. 新 reader_progress 按 (aid, cid) 读取，优先于旧表。
2. 新表没有对应行时，按方向优先读取旧表，并同时校验 aid 和 cid。
3. 旧表只有百分比时，有非空正文才转换为字符锚点并写入新表；空正文只返回临时百分比，不落库。
4. 早期错误行若 charOffset == 0 且 normalizedPercent > 0，正文可用时按百分比重建偏移，再写回修复后的锚点。
5. 新表读取、旧表读取和迁移写入均以取消异常为界；普通数据库异常按回退/不阻塞打开章节处理。
6. 迁移读取和保存由 RoomReaderProgressStore 的互斥锁串行化，避免旧百分比覆盖并发产生的新锚点。

百分比契约：fromPercent 保留用户输入的整数显示百分比；字符偏移使用 UTF-16 下标。恢复到正文后，clamp 会根据实际字符偏移重新计算百分比。

## 双写和降级边界

- 横向页码保存会把真实页码写入旧 location，同时写入新的字符锚点。
- 竖向 reporter 计算章节局部的旧滚动坐标后才允许镜像写入；不会把连续阅读会话的全局 scrollY 直接写入旧表。
- anchor-only 保存不会把字符偏移伪装成旧页码或旧滚动坐标。
- 新表是当前真源；旧镜像失败不会阻塞阅读。
- 已发布的旧版本无法理解新数据库版本，因此“降级”只能在旧版本包含反向迁移时安全完成。当前契约保证升级和旧记录读取；降级安装验证仍需在目标旧 APK 上执行，不使用 destructive fallback。
- 历史 cid-only 兼容 API 已标记 deprecated；新阅读路径必须提供 ChapterRef。

## 验证矩阵

- JVM：ReaderProgressMapperTest、ReaderProgressStoreTest 覆盖横/竖旧记录、空正文、占位修复、非整除百分比、双写、aid 隔离。
- Instrumentation（依赖可用后）：使用 MigrationTestHelper 验证 reader_data 1→2→3、horizontal_read_history 1→2、vertical_read_history 1→2，并检查原有行和复合主键。
- 手工：旧版本记录升级、同 cid 不同 aid、进程杀死后恢复、网络不可用但缓存存在。

执行命令：

    .\\gradlew.bat :app:testDebugUnitTest --no-daemon
    .\\gradlew.bat :app:connectedDebugAndroidTest --no-daemon

当前环境已知限制：JitPack 上 PhotoView、rxhttp、ByRecyclerView、ExpandableTextView、ExpandableRecyclerView、crashx 和 Channel 依赖解析失败，因此不能把历史 build artifacts 当作本轮迁移验证结果。
