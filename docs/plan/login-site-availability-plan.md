# 登录站点可用性修复方案

本文针对"当前登录站点的问题"给出结论与修复方案。结论来自本机实测（2026-10-11 与 2026-10-12，Windows，直连，无代理），
参考实现为同为文库8三方客户端的 [niuhuan/wild](https://github.com/niuhuan/wild)（Flutter + Rust）。

> **2026-10-12 更新：登录已能进入，但详情页与书架仍失败，根因是另外两条 WAF 规则。**
> 见文末「八、后续排查」——`charset` 查询参数会被边缘 WAF 直接拦截，`reader.php` 还要求带 Referer。
> 两条规则均已修复（`Wenku8WebUrls` + `Wenku8WebHeadersInterceptor`）。

## 一、结论

登录失败的根因不是密码、验证码或代理，而是**请求被 Cloudflare 挡在站点之外**。
真机复测后确认有**两个独立成因**，必须同时修正：

1. `www.wenku8.cc` 对所有客户端返回 Cloudflare 质询（`403` + `Cf-Mitigated: challenge`），OkHttp 无法通过。
2. `www.wenku8.net` 会**按 User-Agent 区别对待**：官方 App 的 `Dalvik/...` UA 放行，浏览器 UA 一律 `403`。
3. `www.wenku8.net` 还会**按 `Accept-Encoding` 区别对待**：带 `gzip` 一律 `403`，`identity` 或缺失才放行。
   而 **OkHttp 默认给每个请求自动加 `Accept-Encoding: gzip`**，所以 App 必然被拦。
4. 当前分支把默认节点指向了被全量拦截的 `.cc`，并且 `getWenku8Node()` 每次读取都把 `.net` 改写回 `.cc`。
5. 即使修正节点，`Network.kt` 仍在用 Chrome 桌面 UA 发请求，照样被拦。

也就是说：**节点选错 + UA 用错 + 编码协商用错**，三个问题叠加，任一存在都会让登录必然失败。

> [!IMPORTANT]
> 早期只在主机上用 `curl` 验证，得出"UA 是唯一根因"的结论是**不完整**的：`curl` 默认不发
> `Accept-Encoding`，所以它一直能通；App 走 OkHttp 自动加 gzip，所以一直 403。这个差异
> 只有在真机上对比才暴露出来。详见 2.6。

## 二、实测证据

### 2.1 节点维度（固定 Chrome 桌面 UA）

| 请求 | `www.wenku8.cc` | `www.wenku8.net` |
| --- | --- | --- |
| `GET /login.php` | `403`（`Cf-Mitigated: challenge`） | `403` |
| 响应头 | `Server: cloudflare`，body 为 `Just a moment...` | 同上 |

### 2.2 User-Agent 维度（固定 `www.wenku8.net`）

| User-Agent | `GET /login.php` | 说明 |
| --- | --- | --- |
| `Dalvik/2.1.0 (Linux; U; Android 15; 23114RD76B ...)` | **`200`**（7252B HTML） | 官方 App 身份，放行 |
| Android WebView UA（`...; wv) ... Mobile Safari/...`） | `403` | 伪装 WebView 也不行 |
| Chrome Mobile UA | `403` | |
| Chrome Desktop UA（**当前代码所用**） | `403` | |
| `curl/8.0` | `403` | |

### 2.3 完整登录链路（`www.wenku8.net` + Dalvik UA，强制 IPv4）

| 步骤 | 结果 |
| --- | --- |
| `GET /`（种 cookie） | `302` |
| `GET /login.php` | `200`，`text/html` |
| `GET /checkcode.php` | `200`，`image/png`，217B，PNG 魔数 `89 50 4E 47` |
| `POST /login.php`（伪凭据） | `200`，GBK 编码的错误页 |
| 会话 Cookie | `PHPSESSID=...` 正常下发 |

链路完整可用，说明**只要用对 UA，登录本身没有问题**。

### 2.4 反向对照：当前代码用的 UA 在验证码这一步就被拦

| 验证码请求 UA | 结果 |
| --- | --- |
| Dalvik（重试 3 次） | `200 image/png` ×2，第 3 次连接超时 |
| Chrome 桌面 UA | `403 text/html` |

登录页的验证码由 Glide 加载，而 Glide 复用 `HikariApp` 里那个未设置 UA 的 OkHttp client，
所以**登录页一打开，验证码图片就是 403**——这正是用户看到的现象。

### 2.5 参考实现的做法（wild）

- 默认节点硬编码为 `https://www.wenku8.net`（`rust/src/wenku8/client.rs:20`）。
- `bookcase_headers()` 显式使用 Dalvik UA，注释写明"避免触发 Cloudflare 的浏览器检测"。
- `checkcode()` 先用首页/`login.php` 建立会话再取图；若返回 HTML 挑战页则抛 `cf_challenge`，
  由前端 WebView 兜底。
- 书架的写入类操作（加书架等）走 WebView 注入 Cookie 执行，不依赖 OkHttp 过 CF。

### 2.6 `Accept-Encoding` 对照（真机，小米 13 / Android 13，同一 UA、HTTP/1.1）

这一组是复测时新增的，也是最初被漏掉的根因。设备上 `curl` 用 Dalvik UA 能拿 200，
而 App 的 OkHttp 用同一个 Dalvik UA 却 403 —— 差异只能在请求头或 TLS 指纹上。
逐个头排除后：

| 附加请求头 | `checkcode.php` |
| --- | --- |
| （无） | **200** |
| `Accept-Encoding: gzip` | **403** |
| `Accept-Encoding: identity` | **200** |
| `Connection: Keep-Alive` | 200 |
| `Accept: */*` | 200 |

OkHttp 默认自动添加 `Accept-Encoding: gzip`（并透明解压），这正是 App 与 `curl` 的差别。
把该头改为 `identity` 后，App 内验证码立即正常显示，登录 POST 也返回了真实业务响应
（`该用户不存在`，而非 Cloudflare 拦截页）。

修复落在 `HikariApp.kt` 的 `Wenku8WebHeadersInterceptor`：对 `www.wenku8.net` /
`www.wenku8.cc` 两个主机把 `Accept-Encoding` 固定为 `identity`；其余主机（图片 CDN、
`app.wenku8.com`）不受影响。

## 三、当前代码的具体问题点

当前分支为 `test/no-bili-source`（HEAD `b082b39`）。注意：`main` 分支（`261b387`）已包含
`edb2309 fix(source): stop forcing the node host onto the one Cloudflare blocks`，但**本分支没有这个提交**。

| # | 位置 | 问题 |
| --- | --- | --- |
| 1 | `AppConfig.kt:61` | `node` 默认值 `www.wenku8.cc`（全量被拦的节点） |
| 2 | `Wenku8Repository.kt:680` | `getWenku8Node()` 把 `.net` 改写回 `.cc`，用户选择被静默覆盖 |
| 3 | `Network.kt:48/59/69/78/86` | `login` / `post` / `get` / `getWithoutCookie` / `getReaderPage` 全部使用 Chrome 桌面 UA |
| 4 | `LoginActivity.kt:222` | 验证码经 Glide 加载，走默认 UA，必然 403 |
| 5 | `HikariApp.kt:64` | Glide/RxHttp 共用的 client 未设默认 UA；OkHttp 自动加 `Accept-Encoding: gzip`，两个原因都导致 403 |
| 6 | `HikariApp.kt:74` | `hostnameVerifier` 全局放行（独立的安全问题，不在本方案范围） |
| 7 | `SplashViewModel.isNotLoggedIn()` | 只看本地 cookie 是否过期，不看服务端是否可达，节点被封时错误地直接判为"未登录" |

仓库中已有一个 WIP stash（`stash@{0}`，标题含 `cloudflare login probe`），其中包含
`CloudflareChallenge`（质询识别）、`ReaderNodeHost`（节点迁移/回退）和
`LoginNodeFailureReportTest`。这些是**可复用的资产**，但它们**都没有改 UA**，
所以单独应用 stash 仍然解决不了登录。

## 四、修复方案

分三步，按依赖顺序推进。第 1 步是核心，必须做；第 2 步决定"换个网络还能不能用"；第 3 步是可选的兜底。

### 步骤 1：统一使用官方 App 的 UA（核心修复）

把发往文库8网页端的请求 UA 统一为 Dalvik 身份。这是实测唯一能稳定通过 Cloudflare 的路径。

- 在 `Network.kt` 抽出常量（建议 `Wenku8Headers.USER_AGENT`，与已有 `BiliAssetHeaders` 对称），
  替换 `login` / `post` / `get` / `getWithoutCookie` / `getReaderPage` 中的 Chrome 桌面 UA。
- `HikariApp.kt` 构建共用 client 时，为 Glide/RxHttp 设置默认 UA，
  否则验证码、封面等图片请求仍是浏览器身份（问题点 4、5）。
- 保留 `getFromAppWenku8Com` 的 Dalvik UA 不变。

验收：`GET /checkcode.php` 返回 `image/png`；登录页能正常显示验证码。

### 步骤 2：修正节点选择（默认值与迁移）

- `AppConfig.kt` 默认节点改为 `www.wenku8.net`。
- 删除 `getWenku8Node()` 中把 `.net` 改写回 `.cc` 的逻辑（等价于把 `edb2309` 移植到本分支）。
- 增加一次性迁移 `hasMigratedNodeHost`：把历史被强制的 `.cc` 改回 `.net`，
  但保留用户手动选择的值。
- 设置面板继续保留两个节点，但把 `.cc` 标注为"可能不可用"。

可直接复用 `stash@{0}` 中的 `ReaderNodeHost` 与其测试。

验收：全新安装与"曾经用过旧版本"的升级安装，都能落到可访问的节点。

### 步骤 3：质询识别与节点回退（健壮性，可选）

从 `stash@{0}` 引入 `CloudflareChallenge.isEdgeBlocked(...)`，在 `login()` 与
`isOnline()` 中识别 `Cf-Mitigated` 头 / `Just a moment...` 正文：

- 命中时不要报成"403 服务器拒绝请求"或"登录失败"（会误导用户去查代理），
  而应给出明确文案，并尝试另一个节点。
- 注意节点回退必须**先探测再取验证码**：验证码与会话绑定，
  跨节点提交只会得到"校验码错误"，把用户引向错误方向。

验收：节点被封时，用户看到的是"节点不可用，已尝试另一节点"而非"密码错误"。

## 五、已知风险与边界

- **UA 方案本质上是对 Cloudflare 策略的适配，不是绕过**：站方随时可能收紧，
  例如把放行规则改成同时校验 TLS 指纹或 `Accept-*` 头。方案生效期间也需持续观察。
- **部分路径仍可能间歇性 403**：实测 `modules/article/toplist.php` 即使使用 Dalvik UA
  也出现过 `403` / 连接超时。排行榜等页面可能需要单独的失败重试与降级提示。
- **不应引入 WebView 兜底之外的更重方案**：wild 用 WebView 处理书架写操作，
  但那需要新增 `webview_flutter` 级别的依赖与 JS 桥接，成本与维护面都远大于 UA 修复，
  在当前"稳定性优先"的维护目标下不建议纳入本次范围。
- **不要顺手改 `hostnameVerifier`**：全局关闭主机名校验是独立的安全问题（README 已记录），
  与本方案无耦合，避免把两个改动混在同一个提交里。

## 六、验收清单

1. `GET /checkcode.php`（应用内）返回图片而非 HTML。
2. 登录页能显示验证码，输入正确凭据后进入 `MainActivity`。
3. 节点设置切到 `.cc` 时给出明确的"可能不可用"提示，切到 `.net` 可正常登录。
4. 旧版本升级安装（存储里为 `.cc`）首次启动后能正常登录。
5. 断网 / 节点被封时，提示语指向节点而非密码或代理。
6. `:app:testDebugUnitTest`、`:app:compileDebugKotlin` 通过；新增节点与质询识别测试。

## 七、与现有资料的关系

- 本方案不替代 `stash@{0}` 中的阅读器设置（round5）改动，二者可分别处理；
  只复用其中的 `CloudflareChallenge`、`ReaderNodeHost` 及其测试。
- `README.md` 中"上游遗留问题"表里的 TLS / 明文流量条目与本方案无关，保持原状。

## 八、后续排查：详情与书架仍然失败（2026-10-12）

UA 与 `Accept-Encoding` 修复后，登录能进、推荐能出，但详情页与书架仍报网络错误。
继续实测，发现**另外两条独立的边缘 WAF 规则**，且都只影响部分端点——这解释了
"首页正常、详情/书架失败"的割裂现象。

### 8.1 查询串里的小写字面量 `charset` 会被直接拦截

Cloudflare 对查询串中包含小写子串 `charset` 的请求返回 `403`（"Attention Required!" 页）：

| 查询串 | 结果 |
| --- | --- |
| `?classid=0` | 302 |
| `?classid=0&foo=bar` | 302 |
| `?classid=0&charset=gbk` | **403** |
| `?classid=0&CHARSET=gbk` | 302 |
| `?classid=0&charset2=x` | **403** |
| `?classid=0&xcharset=x` | **403** |
| `?classid=0&ch%61rset=gbk` | 302 |

匹配是**大小写敏感的原始子串匹配**，不是参数名匹配（`charset2`、`xcharset` 也命中）。
把参数名的一个字母百分号编码（`charset` → `ch%61rset`）后，原始串不再包含 `charset`，
而服务端仍能正确解码并按 GBK/BIG5 输出。

这正好解释了症状：`articleinfo.php?...&charset=gbk` 与 `bookcase.php?classid=0&charset=gbk`
被 WAF 拦，而首页的 `index.php?charset=gbk` 恰好不在拦截范围内。

实测矩阵（均带 Referer）：

| 端点 | 字面量 `charset` | 编码 `%61` |
| --- | --- | --- |
| `index.php` | 302 | 302 |
| `articleinfo.php` | 200 | 200 |
| `reader.php` | 200 | 200 |
| `bookcase.php` | **403** | 302 |
| `toplist.php` | 302 | 302 |
| `tags.php` | 302 | 302 |
| `reviews.php` | 200 | 200 |
| `search.php` | **403** | 302 |
| `articlelist.php` | 302 | 302 |
| `uservote.php` | 302 | 302 |
| `addbookcase.php` | 302 | 302 |
| `userpage.php` | 302 | 302 |
| `userdetail.php` | 302 | 302 |

### 8.2 `reader.php` 缺少 Referer 会被质询

章节目录 `modules/article/reader.php` 在**没有 Referer** 时返回 Cloudflare 质询
（`Cf-Mitigated: challenge`），带任意同站 Referer 则返回真实页面，3/3 稳定。
其余端点带不带 Referer 都正常，因此统一补一个同站根 Referer 即可。

### 8.3 修复落点

- 新增 `Wenku8WebUrls`：`hideCharsetParamName()`（把查询串里的 `charset=` 改名为 `ch%61rset=`）
  与 `refererFor()`（同站根 Referer）。纯函数，附 10 个单测。
- `HikariApp.Wenku8WebHeadersInterceptor` 在已有的 UA / `Accept-Encoding` 规则上，
  对两个 web 节点追加这两条；仅在有查询串时改写 URL，避免产生尾随 `?`。
- `Wenku8Repository.getChapter()` 原先把 `reader.php?aid=X` 拼成 `...?aid=X?charset=gbk`（双 `?`），
  已改为 `&` 追加。

### 8.4 排查中排除的项

- **插图域名 `pic.777743.xyz`**：实测 `200 image/jpeg`，且该域名是**站点自己在正文里返回的**，
  不是 app 重写（`Wenku8Parser.replaceDomain` 的默认值只是恰好相同）。无需改动。
- **跳转链**：未登录时各端点 302 到 `login.php?jumpurl=...charset=...`，跳转目标 200 正常；
  `charset` 出现在 `jumpurl` 的**值**里不触发 WAF。
- **`.cc` 节点**：与 `.net` 规则一致（早期测到的 `.cc` 全 403 是因为当时用了浏览器 UA）。
- **图片 CDN 与 app API**：`img.wenku8.com`、`app.wenku8.com` 均正常，不受以上规则影响。
