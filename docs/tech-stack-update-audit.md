# 技术栈更新审查

- 审查日期：2026-09-21
- 审查范围：构建工具链、SDK 版本、全部声明的依赖
- 数据来源：项目内 `build.gradle.kts` / `gradle.properties` / `gradle-wrapper.properties` / `.github/workflows`，版本数据取自 Google Maven、Maven Central 的 `maven-metadata.xml` 与官方 Release Notes
- 说明：本文所有版本号均为 **2026-09-21 时点的仓库最新版本**，未做实际升级验证，结论标注为「静态审查」

---

## 一、结论摘要

**需要更新，但优先级取决于项目定位。** 关键判断：

1. **有一项已经过期的硬性合规要求**：`targetSdk` 停在 34（`app/build.gradle.kts:16`），而 Google Play 自 **2026-08-31** 起要求新应用与应用更新必须 target API 36。该 deadline 已过去 21 天。若本项目仍需分发/上架，这是阻断项。
2. **工具链落后约一个大版本周期**：AGP 8.13.2 → 9.2.1，Gradle 8.13 → 9.4.1+，Kotlin 2.1.20 → 2.4.20。AGP 9 是破坏性大版本，**不能只改版本号**。
3. **有一处「伪依赖」**：`androidx.lifecycle:lifecycle-extensions:2.2.0` 自 2018 年后停止更新，官方已废弃多年，仍在 `app/build.gradle.kts:110` 使用。
4. **有一个版本被注释明令锁死**：MMKV 1.3.14（`app/build.gradle.kts:127-129` 写明「请勿修改此版本，否则 app 会不支持 32 位设备」），这是有意为之的设计约束，不是疏漏。
5. **真正的构建痛点不在版本落后，而在 JitPack**：`README.md` 已记录本地构建会因多个 JitPack 依赖无法解析而在 `dataBindingMergeDependencyArtifactsDebug` 阶段中断。这批个人维护库（PhotoView、ByRecyclerView、medusa、crashx、immersionbar、rxhttp、Channel 等）才是稳定性风险的主要来源。

**如果本项目只是归档**（`README.md` 首行声明「已永久停止更新」），则**不需要**做工具链升级，只需处理 JitPack 解析问题即可恢复可构建状态。
**如果仍要分发**，则必须走完下文阶段 A→C。

---

## 二、基线快照

| 项 | 当前值 | 位置 |
|---|---|---|
| Gradle | 8.13 | `gradle/wrapper/gradle-wrapper.properties:4` |
| AGP | 8.13.2 | `build.gradle.kts:3,6` |
| Kotlin (KGP) | 2.1.20 | `build.gradle.kts:4` |
| KSP | 2.1.20-1.0.31 | `build.gradle.kts:5` |
| Hilt | 2.56 | `build.gradle.kts:7` |
| JDK / jvmTarget | 17 | `app/build.gradle.kts:63-70`、`.github/workflows/reader-regression.yml` |
| compileSdk | 35 | `app/build.gradle.kts:11` |
| targetSdk | 34 | `app/build.gradle.kts:16` |
| minSdk | 24 | `app/build.gradle.kts:15` |
| 依赖声明方式 | 硬编码字符串，无 version catalog | `app/build.gradle.kts:85-167` |
| 代码混淆 | release 未开启（`isMinifyEnabled = false`） | `app/build.gradle.kts:43` |

---

## 三、构建工具链对照

| 组件 | 当前 | 最新（2026-09） | 差距 | 备注 |
|---|---|---|---|---|
| Gradle | 8.13 | 9.4.0（稳定）/ 9.7.0-RC3 | 大版本 | AGP 9.2 要求 **最低 Gradle 9.4.1** |
| AGP | 8.13.2 | 9.2.1 | 大版本 | 破坏性变更见第五节 |
| Kotlin | 2.1.20 | 2.4.20 | 中版本 ×3 | 2.4.0 起移除 `-language-version=1.9` 与 K1 编译器 |
| KSP | 2.1.20-1.0.31 | 2.3.12 | 版本方案变更 | 已改为**独立版本号**，不再带 Kotlin 前缀 |
| Hilt | 2.56 | 2.60.1 | 小版本 ×4 | 2.56 已可用，非必须 |
| JDK | 17 | 17 | 一致 | AGP 9.x 最低仍为 17，**无需动** |
| compileSdk | 35 | 36 / 36.1 | 中版本 | AGP 9.2 最高支持 API 37.0 |
| targetSdk | 34 | 36 | **已过期** | Play 要求 2026-08-31 生效 |

**KSP 的版本方案变更值得单独说明**：KSP 已从 `2.1.20-1.0.31` 这类「Kotlin 版本 + KSP 版本」拼接方案，切换到独立递增的 `2.3.x` 序列。KSP 2.3.10 修复了 Kotlin 2.4.0 的模块名兼容问题，2.3.11 修复了「AGP 9 内置 Kotlin 开启时 R 类解析失败」（google/ksp#2857）——**这两条正是本项目升级路径上的必经坑，说明 KSP 侧已经准备好了**。另：KSP 2.3.12 将最低 AGP 要求提升到 8.12.0，本项目 8.13.2 满足。

---

## 四、依赖对照

### 4.1 AndroidX

| 依赖 | 当前 | 最新 | 位置 |
|---|---|---|---|
| androidx.core:core-ktx | 1.16.0 | **1.19.0** | :87 |
| androidx.appcompat:appcompat | 1.7.1 | **1.8.0** | :88 |
| com.google.android.material:material | 1.13.0-**alpha14** | **1.14.0（稳定）** | :89 |
| androidx.constraintlayout | 2.2.1 | 2.2.2 | :90 |
| androidx.recyclerview | 1.4.0 | 1.4.0 ✅ | :91 |
| androidx.viewpager2 | 1.1.0 | 1.1.0 ✅ | :92 |
| androidx.navigation:* | 2.9.0 | **2.10.1** | :93-94 |
| androidx.room:* | 2.7.1 | **2.8.5** | :97-99,163 |
| androidx.lifecycle:lifecycle-extensions | 2.2.0 | **已废弃** | :110 |
| androidx.lifecycle:lifecycle-livedata-ktx | 2.9.1 | **2.11.0** | :111 |
| androidx.swiperefreshlayout | 1.1.0 | 1.2.0 | :136 |

### 4.2 测试

| 依赖 | 当前 | 最新 |
|---|---|---|
| androidx.test.ext:junit | 1.2.1 | 1.3.0 |
| espresso-core | 3.6.1 | 3.7.0 |

### 4.3 第三方

| 依赖 | 当前 | 最新 | 风险 |
|---|---|---|---|
| com.squareup.okhttp3:okhttp | 4.12.0 | **5.5.0** | 大版本，API 有变更 |
| org.jsoup:jsoup | 1.19.1 | 1.23.2 | 低 |
| com.google.code.gson:gson | 2.11.0 | 2.14.0 | 低 |
| com.github.bumptech.glide | 4.16.0 | **5.0.9** | 大版本 |
| kotlinx-coroutines-* | 1.10.1 | 1.11.0 | 低 |
| com.google.dagger:hilt-android | 2.56 | 2.60.1 | 低 |
| com.squareup.leakcanary | 2.14 | 2.14 ✅ | 3.x 仍为 alpha，**不动** |
| com.tencent:mmkv | 1.3.14 | 2.4.2 | ⚠️ **锁死，勿动**（见第六节） |

### 4.4 JitPack 依赖（无自动核验，需人工评估）

这些是构建稳定性的主要风险面，均为个人/小团队维护：

`PhotoView 2.3.0`（上游仓库已归档）、`ByRecyclerView 1.3.7`、`medusa 0.12.1`、`ExpandableTextView v1.6.1-x`、`ExpandableRecyclerView 0.9.3`、`crashx v6.0.19`、`immersionbar 3.2.2`、`Channel 1.1.5`、`rxhttp 3.2.4`。

其中 **PhotoView** 的上游已归档停更，若后续要升 `compileSdk`/`targetSdk` 或更换图片查看实现，它是第一个该被替换的（可考虑 AndroidX 官方或仍在维护的缩放方案）。**rxhttp** 是网络层核心，且带 KSP 编译器（`rxhttp-compiler`），升级 Kotlin/KSP 时它必须同步跟上，否则编译失败——这是升级路径上的强耦合点。

---

## 五、风险分级

### P0 — 阻断级 / 时效性

1. **targetSdk 34 已不满足 Google Play 要求**（`app/build.gradle.kts:16`）。2026-08-31 起新应用与更新必须 target API 36。若仍要上架，这是硬阻断。
2. **AGP 9 默认启用内置 Kotlin，与 `org.jetbrains.kotlin.android` 插件不兼容**。项目 `build.gradle.kts:4` 与 `app/build.gradle.kts:3` 正是用这个插件。升 AGP 9 必须迁移到内置 Kotlin，或显式设置 `android.builtInKotlin=false` 退出——**该退出开关将在 AGP 10.0（2026 年中）移除**，属于迟早要还的技术债。
3. **AGP 9 默认 `android.newDsl=true`**，旧 DSL 类型（如 `BaseExtension`）不再可用。可用 `android.newDsl=false` 临时回退，同样在 AGP 10.0 移除。

### P1 — 应尽快处理

4. Gradle 8.13 → 9.4.1+（AGP 9.2 的硬性前置条件）。
5. Kotlin 2.1.20 → 2.4.x。注意 2.4.0 移除 K1 编译器与 `-language-version=1.9`；本项目未显式设置 language version，风险较低，但需确认无第三方插件依赖 K1。
6. **移除 `lifecycle-extensions`**（`:110`）。该库 2018 年后未再发布，官方早已拆分废弃。项目同时引入了 `lifecycle-livedata-ktx`，需确认 `lifecycle-extensions` 是否实际还在被引用，能删则删。
7. `material 1.13.0-alpha14` → `1.14.0` 稳定版（`:89`）。**生产项目依赖 alpha 版本本身就是隐患**。
8. Room 2.7.1 → 2.8.5。项目有 **6 个 Room 数据库**（`app/schemas/` 下 6 个目录），升级前必须逐一验证 schema 迁移，这是升级中风险最高的一项。

### P2 — 机会性

9. okhttp 4.12.0 → 5.5.0、glide 4.16.0 → 5.0.9。均为大版本，有 API 变更，建议单独排期、单独验证。
10. 其余小版本跟进（core-ktx、appcompat、navigation、constraintlayout、swiperefreshlayout、jsoup、gson、coroutines、测试库）。

---

## 六、已知约束与坑（改动前必读）

| 约束 | 位置 | 说明 |
|---|---|---|
| **MMKV 版本锁死** | `app/build.gradle.kts:127-129` | 注释明确「请勿修改此版本，否则 app 会不支持 32 位设备」。MMKV 2.x 是否放弃 32 位需核实后再议。**当前 1.3.14 是有意为之，不要顺手升级。** |
| **targetSdk 升 35 会破坏 edge-to-edge** | `app/build.gradle.kts:16` | 注释：「暂时不要升到 35，不然之前的 edgeToEdge 效果会消失」。升 36 必须先重做 WindowInsets 适配，这是阶段 C 的主要工作量。 |
| `android.enableJetifier=false` | `gradle.properties:25` | Jetifier 支持在 AGP 9 中已移除，该属性届时可能失效，需在升级后确认。 |
| `android.overridePathCheck=true` | `gradle.properties:26` | 非标准属性，需在 AGP 9 下验证是否仍被识别。 |
| AGP 9 移除 `aidl` / `renderscript` 默认属性 | — | 项目未使用，无影响。 |
| AGP 9.2 收紧 `-keepattributes *Annotation*` 语义 | — | 项目 release 未开混淆（`:43`），影响低。 |
| 签名口令硬编码 | `app/build.gradle.kts:35-37` | 非技术栈问题，但明文 `storePassword`/`keyPassword` 提交进仓库是安全隐患，建议一并处理。 |
| **JitPack 依赖解析失败** | `README.md` | 已知问题，本地构建会在 `dataBindingMergeDependencyArtifactsDebug` 中断。这是当前最实际的痛点，且**与版本升级无关**。 |

---

## 七、建议的升级路径

按风险递增分三阶段，**每阶段独立验证、独立提交**，不要合并。

### 阶段 A — 低风险跟进（不改工具链）

- 跟进 AndroidX 小版本（core-ktx、appcompat、constraintlayout、navigation、swiperefreshlayout）
- `material` alpha → 1.14.0 稳定版
- 移除 `lifecycle-extensions`
- jsoup / gson / coroutines / 测试库小版本跟进
- 验证命令：`./gradlew :app:testDebugUnitTest :app:compileDebugKotlin --no-daemon`

### 阶段 B — 工具链升级

- Gradle 8.13 → 9.4.1+
- AGP 8.13.2 → 9.x，**使用 AGP Upgrade Assistant**（官方提供了升级技能包），重点处理内置 Kotlin 与 newDsl
- Kotlin → 2.4.x，同步 KSP → 2.3.12、Hilt → 2.60.1
- `compileSdk` 35 → 36
- 强耦合点：`rxhttp-compiler` 必须同步支持新 KSP，否则编译直接失败

### 阶段 C — 行为变更与合规

- `targetSdk` 34 → 36，**必须先完成 edge-to-edge 重做**（见第六节约束）
- 大版本依赖：okhttp 4 → 5、glide 4 → 5
- Room 2.8.5 升级 + 6 个数据库的迁移验证

### 与升级无关、但建议先做的事

- 修复 JitPack 依赖解析（`README.md` 记录的构建中断问题）
- 把 `app/build.gradle.kts` 的 30 项硬编码依赖抽到 `gradle/libs.versions.toml`（version catalog），后续升级才可管理

---

## 八、不建议改动的部分

- **JDK 17**：AGP 9.x 最低要求仍是 17，无升级必要。
- **minSdk 24**：Android 7+ 的覆盖面，无特殊理由不动。
- **MMKV 1.3.14**：见第六节，有意锁定。
- **leakcanary 2.14**：已是稳定版最新，3.x 尚在 alpha。
- **recyclerview 1.4.0 / viewpager2 1.1.0**：已是当前最新。

---

## 九、核验方法与不确定性

**已核验**：所有版本号来自 Google Maven / Maven Central 的 `maven-metadata.xml`（含 `<latest>`/`<release>` 字段），工具链兼容性要求来自 Android 官方 AGP 9.0 / 9.2 Release Notes，KSP 版本策略来自 google/ksp Releases。

**未实测**：本次为**静态审查**，未执行任何构建。以下结论需实测确认：
- `android.overridePathCheck`、`android.enableJetifier` 在 AGP 9 下的实际行为
- `lifecycle-extensions` 是否仍被代码实际引用（未做全量引用检索）
- 6 个 Room 数据库在 2.8.5 下的迁移兼容性
- JitPack 各库是否有支持新工具链的版本

**建议下一步**：先明确项目定位（归档 / 继续分发），再决定是否启动阶段 A。
