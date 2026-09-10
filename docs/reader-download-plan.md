# 下载功能完善计划

本计划承接阶段三文档中明确遗留的债务（`docs/reader-data-pipeline-phase3.md` 第 41 行：「尚未解决下载暂停/删除与正在运行协程之间的竞态，也未删除图片文件目录」），并在代码审计基础上把「可用」提升为「可靠、可后台、可观测」。

建议周期约 5–6 周，六个阶段各自可独立发布、验证和回滚。

## 结论摘要

优先级最高的三件事，均在阶段一完成：

1. **删除竞态**：删除整本/单章时不取消正在运行的下载协程，循环内的 `markDownloaded` / `upsertDownload(COMPLETED)` / `chapterCache.put` 会把已删除的任务行「复活」。
2. **图片文件永不清理**：图片落盘到 `filesDir/chapters/<aid>/<cid>/`，但删除只清 DB 与缓存行，磁盘占用只增不减。
3. **阅读器内发起的下载在下载管理页不可见**：阅读器走的是单参 `enqueueDownloads(refs)` 重载，不写 `download_book` 行，而管理页数据源正是 `observeDownloadBooks()`。

## 一、现状与审计结论

### 1.1 现有链路

```text
入口
 ├── NovelInfoContentFragment → NovelInfoViewModel.enqueueDownloads(refs, title, imageUrl, total)
 │      → ReaderRepository.enqueueDownloads(refs, title, ...)  [写 download_book + download_task]
 └── ReadActivity(横/竖) → ReadViewModel → ReaderSessionCoordinator
        → ReaderRepository.enqueueDownloads(refs)              [只写 download_task]

执行
 ReaderRepository.downloadScope (Application 级 SupervisorJob) + 全局 downloadMutex
   └── runPendingDownloads(aid)  ← dao.pendingDownloads(aid) 逐章串行
         ├── cached(ref) ?: load(ref, forceNetwork = true)
         ├── downloadImages(chapter)  → filesDir/chapters/<aid>/<cid>/image_N
         └── chapterCache.put(isDownloaded = true) + dao.markDownloaded + 状态置 COMPLETED

持久化
 download_task(aid, cid)     status ∈ QUEUED/RUNNING/PAUSED/COMPLETED/FAILED, progress
 download_book(aid)          title, imageUrl, totalChapters
 chapter_cache(aid, cid)     isDownloaded, sizeBytes(仅正文 UTF-8 字节)
```

### 1.2 已确认缺陷（按严重度）

标注「确认」的为代码审计直接可验证；标注「推断」的需在阶段零用测试或日志落地确认。

| # | 级别 | 问题 | 位置 | 证据 |
|---|------|------|------|------|
| 1 | P0 | 删除竞态：`deleteBookDownloads` / `deleteChapterDownload` 不取消运行中的协程，删除后循环会 upsert 回任务行并把章节写回缓存，任务「复活」 | `ReaderRepository.kt:233-248` vs `:152-203` | 确认 |
| 2 | P0 | 图片目录从不删除，`sizeBytes` 不计图片，无配额与统计 | `ReaderRepository.kt:284-305`；`ChapterCache.kt:140`；`ReaderDao.kt:113-114` | 确认 |
| 3 | P0 | 阅读器内下载不创建 `download_book` 行 → 下载管理页看不到，且无 `totalChapters` 无法显示进度 | `ReaderRepository.kt:96-117` vs `:119-137`；`DownloadActivity.kt:43` | 确认 |
| 4 | P1 | 单把全局 `downloadMutex`：多本书完全串行，一本书卡住会阻塞其他书；启动恢复对每本书 launch 但全部排队等同一把锁 | `ReaderRepository.kt:82, 140-145, 152` | 确认 |
| 5 | P1 | 无重试策略与退避：`pendingDownloads` 含 `FAILED`，每次启动/入队都无条件重试全部失败章节，无次数上限、无 `nextRetryAt` | `ReaderDao.kt:98`；`HikariApp.kt:78` | 确认 |
| 6 | P1 | `progress` 只有 5 与 100 两个值，Ui 进度条实际是二值；无图片级/字节级进度 | `ReaderRepository.kt:157-186` | 确认 |
| 7 | P1 | 下载依赖 Application 进程存活，无 WorkManager / 前台服务 / 通知；进程被杀即停，退后台易被回收 | `HikariApp.kt:78`；`build.gradle.kts`（无 work-runtime） | 确认 |
| 8 | P1 | 图片下载网络层独立且缺配置：独立 `OkHttpClient()`，无 cookie / referer / 超时设置；整包读入内存 | `Network.kt:19-27`；`ReaderRepository.kt:296` | 确认 |
| 9 | P2 | 整章原子性过强：单张图失败即整章 FAILED，已成功图片下次重下（幂等但浪费流量） | `ReaderRepository.kt:290-304` | 确认 |
| 10 | P2 | 重复入队会把 `COMPLETED` 任务重置为 `QUEUED`（`@Upsert` 覆盖），已下载章节被重下 | `ReaderRepository.kt:101-115` | 确认 |
| 11 | P2 | 图片路径去重靠 `url.startsWith(directory.absolutePath)`，清数据/迁移后绝对路径失效，判断脆弱 | `ReaderRepository.kt:291` | 确认 |
| 12 | P2 | 管理页为每本书开一个 Flow 收集器 + 每本书一个 Job + `notifyDataSetChanged`，书多时 N+1 查询与全量刷新 | `DownloadActivity.kt:44-66` | 确认 |
| 13 | P2 | 章节详情页硬编码中文「删除 / 重试」，未走 `strings.xml`，与 `values-zh-rTW` 不一致 | `DownloadDetailActivity.kt:56-60` | 确认 |
| 14 | P2 | `resumeBook` / `retryBook` 把 RUNNING 任务一并置 QUEUED，可能与正在执行的循环重复入队 | `ReaderRepository.kt:212-214`；`ReaderDao.kt:58` | 推断 |
| 15 | P2 | `runPendingDownloads` 返回 `Boolean` 但所有调用方忽略，失败无上层反馈 | `ReaderRepository.kt:152, 116, 214` | 确认 |
| 16 | P2 | 下载编排无任何单元测试（现有测试覆盖 Loader / Cache / Prefetcher / Coordinator） | `app/src/test/.../repository/reader/` | 确认 |

## 二、总目标

- 下载任务具备**删除原子性**：停止下载后不残留任务行、不残留图片目录、不被复活。
- 下载**与进程解耦**：退后台、进程被杀、设备重启后能继续，并有系统通知可观测。
- 下载**可调度**：跨书并发上限可控，失败有退避与次数上限，重复入队不重下已完成章节。
- 下载**可观测**：真实进度、失败原因、占用空间、每章状态。
- 下载编排**可测试**：核心编排不依赖 Android，纯 JVM 单元测试可覆盖。
- 现有数据库与用户已下载内容平滑兼容，无需清除数据。

## 三、阶段计划

### 阶段零：基线与可测性（0.5 周）

当前 `runPendingDownloads` 直接依赖 `ReaderDao` + `Network` + `Context`，无法在 JVM 测试中驱动。

**工作内容**

抽取接口，为编排建立可替换边界（保持现有行为不变，纯引入）：

```text
data/repository/download/
  DownloadStore.kt        任务与书目的读写抽象（包装 ReaderDao）
  ImageStore.kt           图片落盘 / 删除 / 目录统计抽象
  ImageRemoteSource.kt    图片字节来源抽象（包装 Network.downloadBytes）
  DownloadOrchestrator.kt 从 ReaderRepository 抽出的下载编排
  DownloadState.kt        状态常量与聚合模型
```

**产出**

- `DownloadQueueTest`：入队去重、状态推进、暂停跳过、失败继续、取消后 RUNNING→QUEUED。
- `DownloadImageTest`：落盘幂等、失败清理 `.tmp`、目录删除。
- `docs/reader-download-issues.md`：本计划第 1.2 节表格的落地版，含每项的复现步骤。

**验收标准**

- 编排逻辑可在纯 JVM 测试下运行（无 Robolectric、无真实网络）。
- 本阶段不改变任何可观测行为，现有应用回归无差异。

---

### 阶段一：一致性与竞态修复（1 周）— 最高优先

**工作内容**

1. **每本书持有可取消的 Job**：`DownloadOrchestrator` 维护 `Map<aid, Job>`。`deleteBookDownloads` / `deleteChapterDownload` 先 `cancelAndJoin()` 再删数据，彻底消除「删除后复活」。
2. **写回前校验存活性**：循环内写回状态、`markDownloaded`、`chapterCache.put` 之前，重新校验任务行仍存在（`dao.getDownloadStatus` 非 null）。任务被并发删除时静默放弃本次写回。
3. **图片目录清理**：
   - `deleteChapterDownload(aid, cid)` → 删除 `chapters/<aid>/<cid>` 目录；
   - `deleteBookDownloads(aid)` → 删除 `chapters/<aid>` 整目录；
   - `clearExpiredCache` / `deleteExpiredCache` 同步清理对应图片目录；
   - 失败任务也清理其残留 `.tmp`。
4. **入口收敛**：把 `enqueueDownloads(refs, title, imageUrl, totalChapters)` 作为唯一入队入口。阅读器侧由 `ReadViewModel` / `ReaderSessionCoordinator` 传入 `novel` 元数据，保证阅读器内下载同样出现在管理页。
5. **重复入队保护**：入队时对已 `COMPLETED` 的任务跳过（或保留 `COMPLETED` 状态不降级），避免已下载章节被重下。

**涉及文件**

- `data/repository/ReaderRepository.kt`、`data/repository/reader/ReaderRepositorySessionDataSource.kt`
- `reader/domain/ReaderSessionCoordinator.kt`、`ui/read/horizontal/ReadViewModel.kt`、`ui/read/vertical/ReadViewModel.kt`
- `data/source/local/database/reader/ReaderDao.kt`
- `ui/detail/NovelInfoViewModel.kt`

**验收标准**

- 在下载进行中删除整本/单章：DB 无残留任务行，磁盘无残留目录，任务不再出现；
- 阅读器内发起下载后，`DownloadActivity` 能看到该书及其进度；
- 对已下载章节重复入队，不产生新的网络请求；
- `DownloadQueueTest`、`DownloadImageTest` 覆盖以上场景并通过。

---

### 阶段二：调度、并发与重试（1 周）

**工作内容**

1. **并发模型**：以「每书串行 + 跨书受限并发」替代全局互斥。引入 `Semaphore`（默认 2–3）或每书 `Channel` 队列，去掉 `downloadMutex` 的全局阻塞。
2. **重试与退避**：`download_task` 增加 `retryCount`、`nextRetryAt`。`pendingDownloads` 查询改为 `status IN (...) AND (nextRetryAt IS NULL OR nextRetryAt <= :now)`，最大重试次数（如 3）后置 `FAILED` 并停止自动重试，仅手动重试可再入队。
3. **降级原子性**：单章下载改为「文本成功即算成功，图片部分失败记录 `failedImages` 并标记 `PARTIAL`」，后续可单独补图，避免一张图失败阻塞整章。
4. **进度写实**：`progress` 改为按 `已处理图片/总图片` 或 `已下载章节/总章节` 计算；新增 `downloadedImages` / `totalImages` 字段供 UI 展示。
5. **返回值生效**：`runPendingDownloads` 的结果回传上层，用于通知与 UI 提示。

**涉及文件**

- `data/repository/download/DownloadOrchestrator.kt`
- `data/source/local/database/reader/ReaderEntities.kt`、`ReaderDao.kt`、`ReaderDatabaseMigrations.kt`

**验收标准**

- 两本书同时下载时互不阻塞（日志可验证并发度上限）；
- 断开网络的章节按退避重试，达到上限后停止并保留 `FAILED`；
- 已下载章节不再被重复请求；
- 进度值随图片/章节推进单调增长，而非只有 5/100。

---

### 阶段三：后台与生命周期（1 周）

**工作内容**

1. **引入 WorkManager**（`androidx.work:work-runtime-ktx`），`CoroutineWorker` + `ExistingWorkPolicy.KEEP` 的唯一队列：
   - 支持 `NetworkType.CONNECTED` 约束（可选「仅 Wi-Fi」开关）；
   - 进程被杀、设备重启后由系统重新调度；
   - 与阶段二的 `DownloadStore` 共用任务表，Worker 只负责「被唤醒后驱动 `runPendingDownloads`」。
2. **通知**：进度通知（书名 + 已完成/总章节 + 百分比）、完成通知、失败通知；点击进入 `DownloadActivity`。通知渠道与权限按 `minSdk 24` 兼容处理。
3. **启动恢复迁移**：`HikariApp.onCreate` 的 `resumeQueuedDownloads()` 改为「重置 RUNNING → 调度 Worker」，保留原方法为兼容入口。
4. **前台服务兜底（可选）**：长任务在 Android 8+ 后台限制下，若需持续下载，改用 `ForegroundService` 承载体，Worker 负责触发。

**涉及文件**

- 新增 `data/repository/download/DownloadWorker.kt`
- `HikariApp.kt`、`AndroidManifest.xml`、`app/build.gradle.kts`
- 新增 `ui/other/DownloadNotification.kt`

**验收标准**

- 下载中杀进程 / 重启设备后，任务自动续跑且不重复下载已完成章节；
- 退到后台 10 分钟以上仍能完成（设备允许的后台限制内）；
- 通知进度与实际一致，完成后可点击进入管理页。

---

### 阶段四：网络与存储健壮性（1 周）

**工作内容**

1. **统一网络客户端**：图片下载复用主 `OkHttpClient`（含 cookie、`Referer`、`User-Agent`、超时、重定向），或在 `Network` 中集中配置；避免第二个裸客户端。
2. **流式写盘**：`ImageRemoteSource` 改为 `response.body.byteStream()` → `FileOutputStream`，避免整包 `ByteArray` 入内存；写入失败清理 `.tmp`。
3. **磁盘统计与配额**：
   - `ImageStore` 记录并汇总实际占用（图片 + 正文），在设置页展示「已下载空间 / 清理」入口；
   - 达到阈值时提示或阻止新下载（先提示，不做自动删除）。
4. **空间不足降级**：`IOException`（ENOSPC）时给出明确错误文案，而不是笼统 FAILED。
5. **图片去重键修正**：以 `aid/cid/index` 作为稳定键，不再依赖绝对路径前缀判断。

**验收标准**

- 大图章节下载内存峰值不随图片总大小线性增长；
- 设置页能展示下载占用，删除后占用回落；
- 存储写满时任务标记为明确的存储错误，恢复空间后可重试成功。

---

### 阶段五：下载管理 UI 收敛（0.5–1 周）

**工作内容**

1. **聚合查询替代 N 个 Flow**：新增 `observeDownloadSummaries()`，用 SQL 聚合出每本书的 `total / completed / failed / paused / running`，`DownloadActivity` 只订阅一个 Flow。
2. **每本书操作完整**：继续 / 暂停 / 重试失败 / 删除，并对整本提供「仅重试失败章节」。
3. **章节列表分页与信息**：几百章不应全量渲染，改为分页/懒加载；每章显示状态、失败原因、可单独重试或删除。
4. **文案资源化**：`DownloadDetailActivity` 的「删除 / 重试」及所有新增文案进入 `strings.xml`（含 `values-zh-rTW`）。
5. **状态可视化**：管理页区分「队列中 / 下载中 / 已暂停 / 已完成 / 有失败」，而非只显示 `completed/total`。

**涉及文件**

- `ui/main/bookshelf/DownloadActivity.kt`、`DownloadBookAdapter.kt`、`DownloadDetailActivity.kt`
- `res/layout/activity_download.xml`、`activity_download_detail.xml`、`item_download_book.xml`、`item_download_chapter.xml`
- `res/values/strings.xml`、`res/values-zh-rTW/strings.xml`

**验收标准**

- 书籍数量增长时管理页查询次数恒定；
- 每个章节的状态与失败原因可见且可操作；
- 繁体语言环境下无硬编码中文残留。

---

### 阶段六：验证与清理（0.5 周）

**验证场景**

- 断网 / 弱网 / 网络切换 / 代理不可用；
- 下载中删除整本、删除单章、暂停、恢复；
- 下载中杀进程、旋转屏幕、切后台、设备重启；
- 存储写满、清应用数据后重下；
- 重复入队已下载章节、整本几百章下载；
- 阅读器内下载与管理页下载交叉。

**性能与指标**

- 无孤儿图片目录、无复活任务行、无重复网络请求；
- 下载不阻塞阅读加载（阅读首章可见时间无明显变化）；
- 内存不随下载章节数线性增长；
- 下载任务可取消且取消后无残留。

**清理内容**

- 移除 `ReaderRepository` 中的下载编排残留与 `@Deprecated` cid-only 下载 API；
- 删除阶段零引入的过渡适配代码；
- 补充 `docs/reader-download-*.md` 说明并更新 `README.md` 项目状态。

## 四、数据模型变更

阶段二起需要 Room 迁移，遵循项目现有非破坏性迁移约定（参考 `ReaderDatabaseMigrations.kt` 与 `ReaderDatabaseMigrationTest`）。

新增字段（全部可空或带默认值，保证降级可读）：

```text
download_task.retryCount     INTEGER NOT NULL DEFAULT 0
download_task.nextRetryAt    INTEGER            -- 可空
download_task.totalImages    INTEGER NOT NULL DEFAULT 0
download_task.failedImages   INTEGER NOT NULL DEFAULT 0
download_book.status         TEXT               -- 可空，由任务聚合派生
download_book.sizeBytes      INTEGER NOT NULL DEFAULT 0
```

迁移策略：新增列 → 旧数据默认值 → 新写入填充 → 观察一段时间后再评估是否删除冗余字段。迁移与 UI 改动分成不同提交。

## 五、优先级与投入排序

| 阶段 | 内容 | 优先级 | 依赖 |
|------|------|--------|------|
| 零 | 基线与可测性 | — | 无 |
| 一 | 一致性、竞态、可见性 | P0 | 零 |
| 二 | 调度、并发、重试、进度 | P1 | 一 |
| 三 | WorkManager 与通知 | P1 | 二 |
| 四 | 网络与存储健壮性 | P1/P2 | 零 |
| 五 | 管理 UI 收敛 | P2 | 二 |
| 六 | 验证与清理 | — | 全部 |

阶段四与阶段五可与阶段二、三并行，互不冲突（分别只动网络/存储层与 UI 层）。

## 六、风险控制

- **删除竞态修复**是唯一涉及「删除语义」的改动，先补测试再改，保留旧删除路径作为对照一版。
- **数据库迁移与 UI 改动分开提交**；不在同一提交内同时修改网络、数据库与下载 UI。
- **WorkManager 引入后行为变化最大**（原先只有前台可用），应先在阶段三以「Worker 仅驱动既有编排」的最小形态落地，再叠加通知。
- 若阶段三风险超预期，可先只做「前台服务 + 通知」，把 WorkManager 延后，但阶段一、二不受影响。

## 七、建议提交拆分

每个提交保持可编译、可回滚：

1. `refactor: extract download store and image store`
2. `test: add download queue baseline tests`
3. `fix: cancel running downloads before deleting tasks`
4. `fix: delete chapter image directories on removal`
5. `fix: register download book metadata from reader entry`
6. `fix: skip completed tasks on re-enqueue`
7. `feat: add per-book download queues with bounded concurrency`
8. `feat: add retry backoff and partial image downloads`
9. `feat: persist real download progress`
10. `feat: schedule downloads with work manager`
11. `feat: add download progress notifications`
12. `feat: reuse main http client for image downloads`
13. `feat: stream image downloads to disk`
14. `feat: aggregate download summaries in one query`
15. `chore: remove legacy cid-only download APIs`

## 最终判断

执行顺序：

```text
基线与测试
  → 一致性与竞态（删除原子性、图片清理、入口可见性）
  → 调度与并发（每书队列、退避重试、真实进度）
  → 后台化（WorkManager + 通知）
  → 网络与存储加固
  → 管理 UI 收敛
  → 验证与清理
```

最值得立刻开始的是**阶段零 + 阶段一**：删除竞态与图片孤儿目录是当前唯一会造成「用户已删除的内容仍在占用空间 / 自行复活」的正确性缺陷，风险低、收益高，且能为后续调度与后台化提供稳定的测试反馈。
