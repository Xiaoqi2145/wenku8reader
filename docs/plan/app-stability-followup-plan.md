# 网络修复后的稳定性收尾方案

日期：2026-10-14。承接 [登录站点可用性修复方案](./login-site-availability-plan.md)。

网络层恢复后（见 `642f197`），按"稳定性优先"的维护目标复查了登录、书架、
更新检查与启动路径。本文记录这一轮的结论与落点。

## 一、登录 4~5 秒的真实来源

实测（Windows 直连，Dalvik UA + `Accept-Encoding: identity`）：

| 请求 | TTFB | 说明 |
| --- | --- | --- |
| `POST login.php` | 1.19 s | 登录本身并不慢 |
| `bookcase.php?classid=N` ×6 并发 | 0.9~2.6 s | 六个书架页，并发后总耗时 ≈ 最慢的一页 |

站点侧不是瓶颈。慢在客户端流程：**登录成功后必须等六个书架页全部返回才跳转主界面**
（`LoginViewModel.getAllBookshelf()` 里 `awaitAll()` 之后才发 `LogInSuccessEvent`）。
登录 1.2 s + 书架同步 2~3 s 叠加，就是用户看到的 4~5 s。

顺带发现两个相关缺陷：

- 同步跑在 `viewModelScope` 上，而 `LoginActivity` 跳转会 `CLEAR_TASK`，
  该作用域随之取消——网络稍慢时六个请求会被中途杀掉。
- 两处（登录、书架页）书架同步逻辑重复，且都先 `deleteAll()` 再逐个插入。
  一旦请求失败，本地书架已被清空。

## 二、本轮改动

### 2.1 书架同步收敛为 `BookshelfSyncer`

- 新增 `data/repository/BookshelfSyncer.kt`：并发拉取 0~5 共六页，
  **全部失败才算失败**，部分成功仍写入（丢掉已拿到的真实数据没有意义）。
- 新增 `BookshelfDao.replaceAll()`（`@Transaction`）：清空与写入合并为一次事务。
  原实现分多次提交，Room 的失效通知会让观察者看到书架"先空后满"。
- `LoginViewModel`：先发 `LogInSuccessEvent` 进入主界面，同步交给
  `@ApplicationScope`（新增 `data/di/CoroutineModule.kt`）在后台继续，
  不再阻塞跳转，也不会被 `CLEAR_TASK` 取消。
- `BookshelfViewModel`：改用同一个 `BookshelfSyncer`，并显式 `Dispatchers.IO`
  （原实现用默认调度器，Jsoup 解析实际发生在主线程）。

### 2.2 书架同步对话框可能永久卡住

`BookshelfFragment` 的 `syncingDialog` 是 `setCancelable(false)`，
但 `NetworkErrorEvent` 分支没有 `dismiss()`。同步失败后用户被一个无法关闭的
对话框挡住。已改为可空并在成功/失败两条路径都关闭。

### 2.3 更新检查：从"每次都提示"到不再误报

`AppRepository.checkUpdate()` 有两个叠加的问题：

1. 接口指向**已归档的上游仓库**，其最新 tag 是 `bye`（停更通知），
   用户被反复提示去下载一个无法安装的版本。
2. 用**字符串相等**比较版本，`bye` 永远不会等于 `3.9.10`，
   且 debug 包的版本名带 ` DEBUG` 后缀，release 包带 `v` 前缀也会误判。

现改为读取本 fork 的 releases，并用 `VersionTag` 做数值比较：
`bye` / `latest` 这类非版本 tag 直接忽略，`3.9.10 > 3.9.9` 按数值判断。
所有应用内链接（下载、GitHub、帮助、崩溃反馈）也一并从上游仓库
改到 `Constants.REPOSITORY_URL`。

### 2.4 启动路径上可能崩溃的枚举读取

`AppRepository` 里六处 `enumValues<T>()[appConfig.x]` 用 MMKV 中的 int 直接索引。
越界（旧版本写入的 ordinal 在新版本失效）会抛 `IndexOutOfBoundsException`，
而这发生在 `Application.onCreate`、崩溃拦截器安装之前——用户遇到的是
**无法自恢复的启动崩溃循环**。已改为 `enumValue(entries, index, default)` 安全回退。

### 2.5 日志中的会话泄漏

`HikariApp.CookieInterceptor` 把 `jieqiUserInfo` 的完整值、`SplashActivity`
把整个 cookie 都写进 logcat。Android 11 以前同设备其他应用可读日志，
等于把可用会话交出去。已删除这些输出（保留失败诊断用的
`Wenku8CallLogger` 与重试日志，它们不含凭据）。

## 三、复核后判定"不是缺陷"的项

**正文图床 `pic.777743.xyz`**：README 曾记为"被硬编码重写到第三方镜像域"。
实测该域名**是站点自己在正文 HTML 里返回的**：

```
GET https://pic.777743.xyz/3/3825/172100/212103.jpg  -> 200 image/jpeg (286 KB)
GET https://img.wenku8.com/3/3825/172100/212103.jpg  -> 302 -> /deny.txt -> 403
```

即换回 `img.wenku8.com` 反而拿不到图。`Wenku8Parser.replaceDomain` 的默认值
只是与站点返回值恰好同名，保留不动。

## 四、验证状态

| 项目 | 结果 |
| --- | --- |
| `:app:testDebugUnitTest` | 204 个用例通过（新增 `VersionTagTest` 11 个、`EnumValueTest` 4 个） |
| `:app:compileDebugKotlin` | 通过 |
| `:app:assembleDebug` | 通过 |
| `:app:assembleRelease` | 通过（5 个 APK，V2 签名校验通过） |

**仍未执行**：真机验证。重点验证项为 ①登录后进入主界面的耗时
②书架在同步期间与同步失败后的表现 ③插图章节的图片加载。

## 五、仍未处理的项

以下问题与本轮主题无关，保持原状，已在 README 的"上游遗留问题"表中记录：

- `hostnameVerifier` 全局放行（中间人风险）；
- `usesCleartextTraffic="true"` 与 `http://app.wenku8.com` 签到接口；
- `app/src/key.jks` 与弱口令入库、debug/release 共用签名；
- `targetSdk = 34` 低于 Google Play 的 35+ 要求。

## 六、发布版本号

原 `versionCode 260114` / `versionName 3.9.10` 与上游最后一次发布完全相同。
若不改号，本 fork 的构建在更新检查里与上游 3.9.10 无法区分，用户永远收不到
这些修复。本次改为 `versionCode 261014` / `versionName 3.9.11`。

release 产物（`app/build/outputs/apk/release/`）：

| APK | 大小 |
| --- | --- |
| `app-universal-release.apk` | 10.31 MB |
| `app-arm64-v8a-release.apk` | 8.84 MB |
| `app-armeabi-v7a-release.apk` | 8.62 MB |
| `app-x86_64-release.apk` | 8.83 MB |
| `app-x86-release.apk` | 8.80 MB |

签名校验：V2 方案通过，单签名者，证书 SHA-256
`8e439209aaa25420f75c5fbf37ebe50e78abdd48baab79598fa4b4fc886601c1`（与既有构建一致，
可覆盖安装）。`lintVitalRelease` 无错误。
