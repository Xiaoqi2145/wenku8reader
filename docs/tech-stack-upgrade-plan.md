# 技术栈升级执行方案

- 编写日期：2026-09-21
- 适用前提：仓库重新进入维护状态
- 配套文档：[技术栈更新审查](./tech-stack-update-audit.md)（版本对照与风险分级）
- 诊断方式：**本方案第一节的根因均为实测复现，非推断**

---

## 一、构建卡死的真实根因（已实测）

### 1.1 实测记录

| 配置 | 命令 | 结果 |
|---|---|---|
| JDK 25（原默认） | `./gradlew :app:compileDebugKotlin` | **BUILD FAILED in 5s** |
| JDK 17 | 同上 | BUILD SUCCESSFUL in 3m15s |
| JDK 21 | 同上 | BUILD SUCCESSFUL in 3m17s |
| JDK 21 + 恢复增量编译 | 同上（改 1 个文件） | BUILD SUCCESSFUL in **1m34s** |

> 后两行的 3m17s 是 JDK 切换后的全量重编；1m34s 是增量生效后的实际改码耗时。

失败时的完整异常：

```
* What went wrong:
25.0.4

java.lang.IllegalArgumentException: 25.0.4
    at org.jetbrains.kotlin.com.intellij.util.lang.JavaVersion.parse(JavaVersion.java:307)
    at org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment.<init>(KotlinCoreEnvironment.kt:243)
    at org.gradle.kotlin.dsl.support.KotlinCompilerKt.compileKotlinScriptToDirectory(KotlinCompiler.kt:198)
```

### 1.2 根因一：Gradle daemon 跑在 JDK 25 上（P0，确定性失败）

`./gradlew --version` 的实际输出：

```
Launcher JVM:  25.0.4 (Azul Systems, Inc. 25.0.4+7-LTS)
Daemon JVM:    C:\Program Files\Zulu\zulu-25 (no JDK specified, using current Java home)
```

本机 `JAVA_HOME` 原本为空，PATH 首位是 `C:\Program Files\Zulu\zulu-25\bin\java`，所以 Gradle 直接拿了 JDK 25。（**已于 0.1 修复**）

**Gradle 8.13 的构建脚本编译器不认识 Java 25。** 它在编译 `build.gradle.kts` 时调用 `JavaVersion.parse("25.0.4")`，直接抛 `IllegalArgumentException`。这不是"慢"，是**在配置阶段就必挂，5 秒退出**。

> 注意：`.github/workflows/reader-regression.yml` 用的是 `temurin 17`，所以 **CI 一直是好的，只有本地命令行会挂**。这解释了为什么"经常"卡——取决于当次用哪个 JDK 启动。

### 1.3 根因二：Kotlin 增量编译被全局关闭（P1，性能）

`~/.gradle/gradle.properties` 里有两行本机适配：

```properties
# 本机构建环境适配（临时）：Kotlin 增量缓存关闭失败，改为进程内编译且不增量
kotlin.incremental=false
kotlin.compiler.execution.strategy=in-process
```

实测证明**这是根因一的连带症状**：换到 JDK 17 后增量编译恢复正常（1m44s vs 全量 3m15s）。原注释写着"临时"，但一直没撤——因为撤了就会碰到 JDK 25 的问题，形成死锁。

### 1.4 根因三：JitPack 依赖解析（P1，间歇性）

9 个 JitPack 个人维护库。当前 `~/.gradle/caches/modules-2/files-2.1/` 下**全部已缓存**，所以不是必然失败；但缓存失效或换机器时会卡在 `README.md` 记录的 `dataBindingMergeDependencyArtifactsDebug` 阶段。

### 1.5 附带问题

- **Gradle daemon 堆积**：`--status` 显示 `1 busy + 2 incompatible + 7 stopped`，多 JDK 切换导致
- **仓库顺序不合理**：`settings.gradle.kts` 里 JitPack 排在 `google()`/`mavenCentral()` **之前**，每个 Google 依赖都要先向响应慢的 JitPack 轮询一次

---

## 二、阶段 0：恢复可构建（当天完成，收益最大）

> 目标：让本地命令行构建稳定跑通，并恢复到「改一个文件 1~2 分钟」的正常节奏。
> **不先做这一步，后面所有升级都无法验证。**

### 0.1 锁定 JDK 21 ✅ 已完成

本机 JDK 分布：

| 路径 | 版本 | 说明 |
|---|---|---|
| `D:\SDK\Java\jdk-21` | Temurin 21.0.11 | **已选用**，与 CI 的 temurin 发行版一致 |
| `C:\Program Files\Zulu\zulu-21` | Zulu 21.0.12 | 备选，实测同样可构建 |
| `C:\Program Files\Java\jdk-17` | Oracle 17.0.7 | 备选，实测同样可构建 |
| `C:\Program Files\Zulu\zulu-25` | Zulu 25.0.4 | **问题来源，不要用** |

已执行：

```bash
setx JAVA_HOME "D:\SDK\Java\jdk-21"
```

> **需要新开一个终端才生效**——`setx` 不改变已运行会话的环境变量。验证方式：新终端里 `./gradlew --version` 的 `Daemon JVM` 应显示 21.x。

> ⚠️ **绝对不要把 `org.gradle.java.home` 写进项目 `gradle.properties`**。CI 跑在 Linux 上，硬编码 Windows 路径会让 CI 直接挂掉。

### 0.2 恢复 Kotlin 增量编译 ✅ 已完成

`~/.gradle/gradle.properties` 中原有的两行已移除：

```properties
kotlin.incremental=false
kotlin.compiler.execution.strategy=in-process
```

**已实测**：改 1 个源文件后编译，耗时从 3m17s 降到 **1m34s**，且 `kspDebugKotlin` 显示 UP-TO-DATE——增量确实生效。

> 若日后增量编译又开始报错，**先确认 JDK 是不是又被换回了不兼容的版本**（本机 PATH 里 zulu-25 仍排在首位，靠 `JAVA_HOME` 覆盖）。

### 0.3 Gradle 性能参数 ✅ 已完成

`gradle.properties` 已调整：

```properties
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=1024m -Dfile.encoding=UTF-8
kotlin.daemon.jvmargs=-Xmx2048m
org.gradle.parallel=true
# org.gradle.caching=true   ← 见下，本机不开启
```

原值是 `-Xmx2048m`——本机 64GB 内存，给 Gradle 2GB 偏小，大项目容易频繁 GC。

**`org.gradle.caching` 已关闭。** 开启后构建会随机失败：

```
Failed to store cache entry ... build-cache-1\xxx.part (拒绝访问。)
```

三次构建里出现两次，涉及 `AarResourcesCompilerTransform` 等任务。这是本机对新建文件的瞬时锁冲突（疑似杀软扫描）被 build cache 放大了——**build cache 会大量写小文件，正好踩在这个环境的弱点上**。稳定性优先，暂不启用；换机器或排除锁冲突后可再打开。

### 0.4 仓库顺序优化 ✅ 已完成

`settings.gradle.kts` 已把 JitPack 移到 `google()`/`mavenCentral()` **之后**。

原因：JitPack 响应慢且常超时，排在前面会让每个 Google/Maven Central 依赖都先向它轮询一次。项目中真正的 JitPack 依赖（PhotoView、ByRecyclerView、medusa、immersionbar、rxhttp、crashx、Channel 等）在前面仓库找不到，仍会正确回落。

**已验证**：`./gradlew :app:dependencies --configuration debugRuntimeClasspath` → `BUILD SUCCESSFUL in 15s`，6 组 JitPack 依赖全部正常解析。

### 0.5 清理 daemon ✅ 待执行

```bash
./gradlew --stop
```

多 JDK 切换会留下 incompatible daemon。建议在切换 JDK 后跑一次。

### 0.6 JitPack 长期加固（可选）

依赖已缓存的库，可启用依赖锁定降低解析失败概率：

```kotlin
// app/build.gradle.kts
dependencyLocking {
    lockAllConfigurations()
}
```
```bash
./gradlew :app:dependencies --write-locks
```

**更彻底的方案**是替换停更库（见阶段 1.3），但工作量大，建议排到后期。

---

## 三、阶段 1：依赖治理（为升级铺路）

### 1.1 引入 version catalog ✅ 已完成

已建立 `gradle/libs.versions.toml`，原 30 项硬编码依赖与 5 个插件版本全部集中管理：

- `[versions]` — 34 个版本号常量（按用途分组，MMKV 处保留了「有意锁定」的警告注释）
- `[libraries]` — 33 个依赖坐标
- `[plugins]` — 5 个插件（AGP ×2、Kotlin、KSP、Hilt）

`build.gradle.kts` 与 `app/build.gradle.kts` 已改为 `alias(libs.plugins.*)` / `libs.*` 形式。后续升级只需改 `libs.versions.toml` 一处。

**踩坑记录**：version catalog 的 alias **不能出现「一个是另一个的前缀」**，否则生成的访问器会冲突。本次调整了三处：

| 原 alias | 改为 | 原因 |
|---|---|---|
| `rxhttp` | `rxhttp-core` | 与 `rxhttp-compiler` 前缀冲突 |
| `glide` | `glide-core` | 与 `glide-okhttp3-integration` 前缀冲突 |
| `hilt-android-compiler` | `hilt-compiler` | 与 `hilt-android` 前缀冲突 |

**已验证**：`:app:dependencies` 解析正常（35s）；`:app:compileDebugKotlin` BUILD SUCCESSFUL。

### 1.2 低风险版本跟进 ✅ 已完成（material 除外）

**前置条件：compileSdk 35 → 36 已先行完成**（见 2.5）。

| 依赖 | 原 | 现 | 说明 |
|---|---|---|---|
| androidx.core:core-ktx | 1.16.0 | **1.18.0** | 未取 1.19.0，原因见下 |
| androidx.appcompat | 1.7.1 | 1.8.0 | |
| androidx.constraintlayout | 2.2.1 | 2.2.2 | |
| androidx.navigation | 2.9.0 | 2.10.1 | |
| androidx.swiperefreshlayout | 1.1.0 | 1.2.0 | |
| androidx.lifecycle:lifecycle-livedata-ktx | 2.9.1 | 2.11.0 | |
| org.jsoup:jsoup | 1.19.1 | 1.23.2 | |
| gson | 2.11.0 | 2.14.0 | |
| kotlinx-coroutines | 1.10.1 | 1.11.0 | |
| androidx.test.ext:junit | 1.2.1 | 1.3.0 | |
| espresso-core | 3.6.1 | 3.7.0 | |
| com.google.android.material | 1.13.0-alpha14 | **未动** | 见下 |

**已验证**：`:app:compileDebugKotlin` BUILD SUCCESSFUL；`:app:testDebugUnitTest` BUILD SUCCESSFUL。

#### 关键发现：AndroidX 已把门槛提到 AGP 9.1.0，依赖升级被工具链卡住

直接读 AAR metadata（`META-INF/com/android/build/gradle/aar-metadata.properties`）的结果：

| androidx.core:core | minCompileSdk | minAndroidGradlePluginVersion |
|---|---|---|
| 1.17.0 | 36 | 8.9.1 ✅ |
| 1.18.0 | 36 | 8.9.1 ✅ |
| **1.19.0** | **37** | **9.1.0** ❌ |

所以 core-ktx 只升到 **1.18.0**——这是当前 AGP 8.13.2 能支持的最高版本。要到 1.19.0，必须先把 AGP 升到 9.1.0+ 并把 compileSdk 提到 37。

**这条约束反转了原计划的顺序**：不是「先升依赖、再升工具链」，而是**必须先升工具链**。详见第七节。

> 这个判定方法值得复用：升级 AndroidX 前先拉 AAR 看 metadata，比反复试错快得多。
> ```bash
> curl -sL -o core.aar "https://dl.google.com/dl/android/maven2/androidx/core/core/1.18.0/core-1.18.0.aar"
> unzip -p core.aar META-INF/com/android/build/gradle/aar-metadata.properties
> ```

#### material 为什么没动

`1.13.0-alpha14` → `1.14.0` 是 alpha 到 stable 的跨越，API 可能变化。本项目是 MD3 风格，大量使用 Material 主题与组件，**UI 回归只有实机才能发现，编译期查不出来**。建议等能实机验证时单独升级、单独提交。

### 1.3 清理僵尸依赖 ✅ 已完成

**已移除 `androidx.lifecycle:lifecycle-extensions`。**

检索结果：代码中**没有**使用该库的独有 API——没有 `ViewModelProviders`（复数，那个已废弃的类；代码用的是现行的 `ViewModelProvider`），也没有 `ProcessLifecycleOwner`。

但直接删会丢掉几个传递依赖（`lifecycleScope`、`withStarted`、`viewModelScope`、`LiveData`），所以改为**显式声明实际用到的组件**：

```kotlin
implementation(libs.androidx.lifecycle.runtime.ktx)    // lifecycleScope / withStarted / repeatOnLifecycle
implementation(libs.androidx.lifecycle.viewmodel.ktx)  // viewModelScope
implementation(libs.androidx.lifecycle.livedata.ktx)   // LiveData / MutableLiveData
```

这比依赖传递更明确——直接使用的 API 就应该显式声明。

**已验证**：`:app:compileDebugKotlin` + `:app:testDebugUnitTest` 均通过。

**待评估**：PhotoView 上游仓库已归档停更，是 `ui/other/PhotoViewActivity.kt` 的依赖，建议后续替换。

### 1.4 Room 升级 ⏸️ 已尝试，推迟到阶段 2 之后

目标 `androidx.room` 2.7.1 → 2.8.5。**实测失败，已回退到 2.7.1。**

失败现象（KSP 阶段）：

```
e: java.lang.AbstractMethodError: Receiver class androidx.room.migration.bundle.FieldBundle$$serializer
   does not define or inherit an implementation of the resolved method
   'abstract kotlinx.serialization.KSerializer[] typeParametersSerializers()'
   of interface kotlinx.serialization.internal.GeneratedSerializer.
```

**排查过程**（以下假设均已排除）：

| 假设 | 结论 |
|---|---|
| Room 2.8.5 要求更高 compileSdk / AGP | 否。`room-ktx` 的 AAR metadata 只要求 `minCompileSdk=34` / `minAGP=8.1.1` |
| Room 2.8.5 要求更高 KSP | 否。它依赖 `symbol-processing-api:2.0.10-1.0.24`，**比项目用的 2.1.20-1.0.31 还旧** |
| classpath 上 serialization 版本解析错了 | 否。`dependencyInsight` 显示 KSP classpath 上解析到 **1.8.1**，与 Room 2.8.5 POM 声明的完全一致 |

**真正根因**：`kotlinx-serialization-core-jvm:1.4.0` 存在于本机缓存，但**不在 Gradle 管理的 KSP classpath 上**——它来自 **KSP 1.0.31 进程自身的运行时 classpath**，不受依赖解析控制。

在 1.4.0 中 `GeneratedSerializer.typeParametersSerializers()` 是**抽象方法**，在 1.8.1 中它有默认实现。Room 2.8.5 的序列化器按 1.8.1 编译（因此没有实现它），运行时却撞上 1.4.0 的接口 → `AbstractMethodError`。

**结论**：这是 **KSP1 的架构问题**，Gradle 层面 force 版本解决不了。**必须先升级 KSP 到 2.x 才能升 Room。**

**行动**：已回退 `room = "2.7.1"`，并在 `libs.versions.toml` 里留了注释说明。Room 升级并入阶段 2 一起做。

> 补充：6 个数据库（`app/schemas/` 下）的 schema 版本是 1~4，但**升级 Room 库版本本身不会改变 schema 版本**（那由 `@Database(version = N)` 决定），所以迁移逻辑不受影响。真正需要的是在设备上验证数据库能正常打开。

---

## 四、阶段 2：工具链升级

> 这是工作量最大、风险最集中的阶段。**务必在阶段 0 完成后开始**，否则无法区分"升级引入的问题"和"环境本身的问题"。

### 2.1 Gradle 8.13 → 9.x ⚠️ 受阻于本机环境

`gradle/wrapper/gradle-wrapper.properties:4`。**前置**：AGP 9.2 要求最低 Gradle 9.4.1，AGP 9.4 要求 9.6.0。

本机 `~/.gradle/wrapper/dists/` 下已有 9.1.0、9.2.0、9.5.0、9.6.1，无需下载。

#### 阻塞：Gradle 9.x 在本机无法并发运行

用本机已有的 Gradle 9.2.0 / 9.6.1 直接跑，配置阶段即失败：

```
java.io.FileNotFoundException: C:\Users\xiaoq\.gradle\caches\9.2.0\transforms\<hash>\<hash>.lock (拒绝访问。)
      ...and 137 more failures.
```

**排查过程**（以下均已排除）：

| 检查项 | 结果 |
|---|---|
| 目录权限 | 正常（`drwxr-xr-x xiaoq`） |
| 手动创建锁文件 | **成功** |
| 手动删除锁文件 | **成功** |
| 是否有进程占用 | 否。只有 `jps` 自己——那条 `10 busy` 是过期的 daemon 注册表条目，不是真实进程 |
| `.gradle` 是否在同步盘 | 否，不在 OneDrive 内 |
| 换 Gradle 版本 | 9.2.0 与 9.6.1 **均失败** |

**定位**：给同一个命令加上 `--max-workers=1` → **BUILD SUCCESSFUL in 2m 18s**。

**结论：并发竞争。** Gradle 9 的 transform 隔离机制在配置阶段会并发请求上百个 transform，本机的文件系统 / 杀软组合在高并发下无法稳定创建锁文件。Gradle 8.x 没有这套机制，所以一直正常。

**这也补上了「经常卡构建」的另一半**——之前归因于 JDK 25，但即使修好 JDK，换到 Gradle 9.x 仍会撞上这个。

> 附带数据：`~/.gradle/caches/9.5.0/.../locks` 下累积了 **10325** 个锁文件，`9.6.1` 下 **4694** 个。Gradle 8.13 / 8.14 / 9.2.0 无此目录。

#### 三个解决方向

| 方案 | 效果 | 代价 |
|---|---|---|
| A. 加杀软白名单 | 很可能根治 | 需手动配置：把 `~/.gradle` 与项目目录加入排除项 |
| **B. `GRADLE_USER_HOME` 移到非系统盘（已采用）** | 很可能根治（社区对同类问题的常见解法：C 盘用户目录权限受限） | 需迁移缓存 |
| C. `org.gradle.workers.max=1` | 立即可用 | 构建明显变慢（无法并行执行任务） |

#### 已采用方案 B：Gradle 缓存迁移到 E 盘

**目标目录**：`E:\SDK\GradleHome`

**做法**：用 robocopy 全量迁移，而不是重新下载依赖。

| 内容 | 大小 | 说明 |
|---|---|---|
| `caches/modules-2` | 3.3G | 依赖缓存，**必须迁移**（否则要重新下载全部依赖） |
| `wrapper/dists` | 2.9G | Gradle 发行版 |
| `caches/<version>` | 各 0.5~5G | transform 缓存，可重建但迁移更省时 |
| `gradle.properties` | 1K | 用户级配置 |

C 盘原目录**保留**作为备份，验证通过后再考虑清理。

**配置方式**：设置用户级环境变量

```bash
setx GRADLE_USER_HOME "E:\SDK\GradleHome"
```

> 这是**用户级**变量，会影响本机所有 Gradle 项目（含 KAmusic）。**需新开终端生效。**

##### 迁移过程中的发现（独立印证了「并发是根因」）

首次用 `robocopy /E /MT:16` 迁移，跑了 **54 分钟几乎没成功**——`wrapper` 只过来 5K（源 2.9G），`modules-2` 只有 5.4M（源 3.3G），日志里大量：

```
错误 5 (0x00000005) 正在创建目标目录 E:\SDK\GradleHome\caches\8.14\transforms\...\transformed\  拒绝访问。
```

**注意：这是 E 盘报的错。**

改用**单线程 `cp -r`** 后，同一个目录**复制成功**。

**这独立印证了「并发是根因」**：`robocopy /MT:16` 的 16 线程并发创建文件，撞上的是同一个锁冲突；单线程则不会。

同时也说明：**问题不是 C 盘用户目录特有的**——E 盘在并发下同样会失败。所以方案 B 是否真能解决 Gradle 9 的锁问题，**必须实测验证**，不能想当然。

**迁移范围**（跳过复制失败的 transforms，它们可以重建）：

| 内容 | 大小 | 必需性 |
|---|---|---|
| `gradle.properties` | 1K | 必需 |
| `wrapper/` | 2.9G | 必需（Gradle 发行版，避免重新下载） |
| `caches/modules-2/` | 3.3G | 必需（依赖缓存，避免重新下载） |
| `caches/<version>/` | 各 0.5~5G | **跳过**，Gradle 会按需重建 |

**当前状态**：迁移进行中，Gradle 版本暂保持 8.13。

> 顺带确认：**AGP 8.13.2 与 Gradle 9.2 是兼容的**（`--max-workers=1` 下配置阶段成功），只输出「使用了 Gradle 10 将移除的废弃特性」的警告。所以 2.1 与 2.2 之间的顺序依赖是成立的，卡点只在环境。

### 2.2 AGP 8.13.2 → 9.x

`build.gradle.kts:3,6`。**AGP 9 是破坏性大版本，不能只改版本号。**

关键破坏性变更（来自 AGP 9.0 Release Notes）：

| 变更 | 默认值 | 影响 |
|---|---|---|
| `android.builtInKotlin` | `false` → **`true`** | 与 `org.jetbrains.kotlin.android` 插件**不兼容** |
| `android.newDsl` | `false` → **`true`** | 旧 DSL 类型（`BaseExtension` 等）不可用 |
| `android.dependency.useConstraints` | `true` → `false` | 依赖约束行为变化 |
| `android.r8.optimizedResourceShrinking` | `false` → `true` | 资源裁剪行为变化 |
| `android.defaults.buildfeatures.resvalues` / `shaders` | `true` → `false` | 未使用，无影响 |
| `android.defaults.buildfeatures.aidl` / `renderscript` 属性 | 移除 | 未使用，无影响 |

**建议分两步走，先保守后激进：**

**2.2a（保守，先跑通）**——升 AGP 9 但显式退出两个新机制：
```properties
# gradle.properties —— 临时回退，AGP 10.0 将移除该开关
android.newDsl=false
android.builtInKotlin=false
```
保持现有构建脚本结构不变，把变量降到最少。

**2.2b（迁移，后续单独提交）**——逐个模块迁移到内置 Kotlin + new DSL，然后删掉上面两行。

> 官方提供 AGP 9 升级助手与升级技能包，建议先跑一遍助手再手工微调。

### 2.3 Kotlin 2.1.20 → 2.4.x

`build.gradle.kts:4`。注意事项：
- Kotlin 2.4.0 起**移除 K1 编译器**与 `-language-version=1.9`。本项目未显式设置 language version，风险较低，但需确认无第三方插件依赖 K1。
- 必须同步：**KSP** → `2.3.12`、**Hilt** → `2.60.1`。

**KSP 已换版本方案**：从 `2.1.20-1.0.31` 这种「Kotlin 前缀拼接」改为独立递增的 `2.3.x`。好消息是升级路径上的两个坑 KSP 侧已填好：
- 2.3.10 修复 Kotlin 2.4.0 兼容
- 2.3.11 修复「AGP 9 内置 Kotlin 下 R 类解析失败」（google/ksp#2857）

### 2.4 强耦合点：rxhttp 必须同步升级

`rxhttp` 带 KSP 编译器（`rxhttp-compiler`，`app/build.gradle.kts:107`），**跟不上新 KSP 就编译直接失败**。

已核实：**rxhttp 仍在维护，最新版本 3.5.1**（当前用 3.2.4）。升级路径是通的，但需验证 3.5.1 是否已适配 KSP 2.3.x——**这是阶段 2 最需要先确认的一项**。

### 2.5 compileSdk 35 → 36 ✅ 已完成（提前到 1.2 之前）

`app/build.gradle.kts:11` 已改为 36。本机已安装 SDK Platform 36 与 build-tools 36.0.0。

**为什么提前**：AndroidX core 1.17+ 的 AAR metadata 要求 `minCompileSdk=36`，不先改这一项，1.2 的依赖升级无法进行。

`compileSdk` 只影响编译期可见的 API，**不改变运行时行为**（那是 `targetSdk` 的事），所以提前做无风险。

> AGP 9.2 最高支持 API 37.0。若日后要用 core 1.19+，还需把 compileSdk 提到 37。

---

## 五、阶段 3：合规收尾

### 3.1 targetSdk 34 → 36（需先做 edge-to-edge）

`app/build.gradle.kts:16` 的注释已经写明：

> `//暂时不要升到35，不然之前的edgeToEdge效果会消失`

所以这**不是改个数字**：targetSdk 35+ 会强制 edge-to-edge，必须先重做 WindowInsets 适配。这是阶段 3 的主要工作量。

**背景**：Google Play 自 **2026-08-31** 起要求新应用与应用更新必须 target API 36。该 deadline 已过，**只要还要分发就是阻断项**。

### 3.2 大版本依赖（单独排期）

- `okhttp` 4.12.0 → 5.5.0（API 有变更）
- `glide` 4.16.0 → 5.0.9（API 有变更）

---

## 六、不要动的东西

| 项 | 位置 | 原因 |
|---|---|---|
| **MMKV 1.3.14** | `app/build.gradle.kts:127-129` | 注释明确「请勿修改此版本，否则 app 会不支持 32 位设备」。升级前必须先核实 MMKV 2.x 是否放弃 32 位 |
| **JDK 版本** | — | 锁 17，与 CI 和 `jvmTarget` 一致，无需追新 |
| **minSdk 24** | `:15` | Android 7+ 覆盖面，无特殊理由不动 |
| **leakcanary 2.14** | `:166` | 已是稳定版最新，3.x 仍在 alpha |
| **recyclerview 1.4.0 / viewpager2 1.1.0** | `:91-92` | 已是当前最新 |
| **`android.overridePathCheck=true`** | `gradle.properties` | 保留，待 AGP 9 下验证其行为 |

---

## 七、执行顺序与验收

### 推荐顺序

```
阶段 0 恢复可构建              ✅ 已完成
   ↓
1.1 version catalog            ✅ 已完成
   ↓
2.5 compileSdk 36              ✅ 已完成（从末尾提前，是 1.2 的前置条件）
   ↓
1.2 低风险依赖                  ✅ 已完成（material 除外）
   ↓
1.3 清僵尸依赖                  ✅ 已完成
   ↓
2.1 Gradle 9.4.1               ← 一次只动一层
   ↓
2.2a AGP 9（保守模式：newDsl=false + builtInKotlin=false）
   ↓
2.3 Kotlin 2.4 + KSP 2.3.12（KSP2）+ Hilt 2.60.1 + rxhttp 3.5.1
   ↓
1.4 Room 2.8.5                 ← 必须等 KSP2 落地后才能做
   ↓
2.2b 迁移内置 Kotlin + new DSL
   ↓
core-ktx 1.19.0 + 其余「要求 AGP 9.1+」的依赖
   ↓
material 1.14.0                ← 需实机验证 UI
   ↓
3.1 targetSdk 36（先做 edge-to-edge）
   ↓
3.2 okhttp 5 / glide 5
```

> **顺序依据**：工具链版本决定了能用哪些依赖版本。core 1.19.0 要求 AGP 9.1.0+ 和 compileSdk 37，所以在 AGP 升级完成前，AndroidX 只能停在 1.18.0。这也是 2.5 被提前到 1.2 之前的原因。

**每个阶段独立提交、独立验证。不要合并提交**——出问题时才能精确定位到是哪一步引入的。

### 验收命令

```bash
# 阶段 0 / 1 验收
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin

# 完整验收
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
pwsh -File scripts/check-galisland.ps1   # 项目自带的行为断言脚本
```

### 回滚

- 配置类改动（`gradle.properties` / `settings.gradle.kts`）：`git revert` 单个提交即可
- 工具链升级：每阶段一个提交，回滚对应提交
- **升级前先打 tag**：`git tag pre-upgrade-$(date +%Y%m%d)`

---

## 八、风险清单

| 风险 | 影响 | 应对 |
|---|---|---|
| AGP 9 内置 Kotlin 与现有插件冲突 | 构建直接失败 | 2.2a 先显式退出（`builtInKotlin=false`） |
| rxhttp-compiler 未适配 KSP 2.3.x | 编译失败 | **升级前先验证 rxhttp 3.5.1** |
| Room 2.8.5 的 6 个库迁移不兼容 | 数据丢失 | 单独提交 + 逐个验证 schema |
| targetSdk 36 破坏 edge-to-edge | UI 回归 | 先做 WindowInsets 适配再改 targetSdk |
| JitPack 库在换环境时解析失败 | 构建卡住 | 依赖锁定 / 替换停更库 |
| `android.overridePathCheck` 在 AGP 9 下行为未知 | 构建失败 | 升级时若报错优先移除该项 |
| **Windows 瞬时文件锁** | 构建随机失败，报 `拒绝访问。` | **直接重试**（多数一次就过）；持续失败再删对应缓存目录 |

> 关于最后一项：本机已两次遇到，分别出现在 `desugar_graph` 和 Gradle build-cache 的 `.part` 文件上。特征是没有 java 进程占锁、文件也无只读属性，属于杀软扫描一类的瞬时锁冲突。**重试即可**，不要急着 `clean` 全量重建。

---

## 九、本次已完成的改动

**项目内改动**（已落到仓库，跨平台安全、不影响 CI）：

1. **`gradle.properties`**：Gradle 内存 2GB → 4GB + Metaspace 1GB；新增 `kotlin.daemon.jvmargs`；开启 `org.gradle.parallel`；**`org.gradle.caching` 保持关闭**（原因见 0.3）
2. **`settings.gradle.kts`**：JitPack 移至仓库列表末尾，避免解析时无谓轮询
3. **新增 `gradle/libs.versions.toml`**：34 个版本号 + 34 个依赖 + 5 个插件集中管理
4. **`build.gradle.kts` / `app/build.gradle.kts`**：改用 `alias(libs.plugins.*)` / `libs.*`
5. **`app/build.gradle.kts:11`**：`compileSdk` 35 → 36
6. **11 项依赖升级**：core-ktx 1.18.0、appcompat 1.8.0、navigation 2.10.1、lifecycle-livedata-ktx 2.11.0、jsoup 1.23.2、gson 2.14.0、coroutines 1.11.0、constraintlayout 2.2.2、swiperefreshlayout 1.2.0、test-ext-junit 1.3.0、espresso 3.7.0
7. **移除 `lifecycle-extensions`**，改为显式声明 `lifecycle-runtime-ktx` + `lifecycle-viewmodel-ktx` + `lifecycle-livedata-ktx`

**本机环境改动**：

8. **用户级 `JAVA_HOME` → `D:\SDK\Java\jdk-21`**（`setx`，**需新开终端生效**）
9. **`~/.gradle/gradle.properties`**：移除 `kotlin.incremental=false` 与 `kotlin.compiler.execution.strategy=in-process`

**验证状态**：`:app:compileDebugKotlin` 与 `:app:testDebugUnitTest` 均 BUILD SUCCESSFUL。

**已回退**：Room 2.8.5（见 1.4，需 KSP2 才能升）。

**待执行**：0.5 清理 daemon（可选）；阶段 2 工具链升级。

---

## 十、下一步

**阶段 0 与阶段 1 已全部完成。** 本地构建从「5 秒必挂」恢复到「改一个文件 1m34s」；依赖集中到 version catalog；AndroidX 跟进到当前工具链支持的上限；僵尸依赖已清理。

**剩下的都在阶段 2，而且它们互相咬合，必须按顺序来：**

```
2.1 Gradle 9.4.1
  → 2.2a AGP 9（保守模式）
    → 2.3 KSP 2.3.12（KSP2）
      → 1.4 Room 2.8.5          ← 卡在这里，KSP2 是前提
      → 2.2b 内置 Kotlin + new DSL
        → core-ktx 1.19.0 / material 1.14.0
```

**三件事都在等工具链**：Room 2.8.5 等 KSP2（1.4 的结论）、core-ktx 1.19.0 等 AGP 9.1+、material 1.14.0 等实机验证。所以阶段 2 不是「可选项」，而是解锁其余全部工作的前置。

**动手前务必先打 tag**：

```bash
git tag pre-agp9-upgrade
```

AGP 9 的迁移是整条路径上最重的一步，且内置 Kotlin 的退出开关会在 AGP 10.0 移除——这一步迟早要走，但要留出整块时间。
