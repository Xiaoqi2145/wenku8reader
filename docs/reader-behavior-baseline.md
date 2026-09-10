# 阅读器行为基线

本基线对应 plan.md 阶段一，目标是在阅读核心重构前固定当前可观察行为。基线只依赖 JVM 测试，不启动 Android UI。

## 固定 fixture

| fixture | 覆盖内容 |
| --- | --- |
| reader_empty.html | #content 为空时解析为空字符串 |
| reader_short.html | 单段短章节 |
| reader_long.txt | 超长、多段、顺序稳定的纯文本 |
| reader_images.html | 正文、图片 URL、正文尾部文字 |
| reader_anchor.txt | 开头/中间/结尾锚点和换行段落 |
| reader_simple.html | 标题、多个段落和换行 |

## 当前约定

- 阅读内容偏移使用 Kotlin/Android 的 UTF-16 字符偏移。
- 百分比输入被限制在 0..100；空内容的偏移和百分比均为 0。
- ReaderAnchor.paragraphIndex 是锚点前换行符数量。
- HTML 阅读正文从 #content 提取；图片单独从 img[src] 提取。
- 图片 URL 会转换为 HTTPS，并经过当前域名替换逻辑。
- reader_progress 锚点表是当前真源；横向会镜像真实页码，竖向只镜像 reporter 计算的章节局部旧坐标，anchor-only 保存不会污染旧 location 字段。
- 旧记录首次按方向读取时惰性迁移；有非空正文时按百分比推导字符偏移，无正文或空正文时仅返回临时百分比锚点而不落库。旧占位行会在正文可用时按百分比修复。
- reader_data、横向历史和竖向历史均通过非破坏性迁移使用 (aid, cid) 复合主键，避免同 cid 跨书覆盖。

## 验证命令

    .\\gradlew.bat :app:testDebugUnitTest --no-daemon

ReaderBehaviorTest、ChapterBlockMapperTest 和 ChapterNavigatorTest 是重构前的 JVM 回归入口。ChapterBlockMapperTest 当前覆盖 11 个纯领域行为断言，ReaderAnchorTest 覆盖 3 个锚点断言。

若构建环境无法解析外部依赖，应保留完整 Gradle 错误，不把历史构建报告当作当前验证结果。本轮已用独立 Kotlin/JUnit 编译运行纯领域测试；完整 Gradle 任务仍需要可用的 JitPack 依赖缓存或网络。
