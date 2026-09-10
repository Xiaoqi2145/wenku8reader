# GalIsland 原生视觉对齐

参考：`E:\Tool\Galisland-UI-Template` 的 tokens、components、layout 与预览图。

## 已实现

- 明暗主题的 sidebar / toolbar / input / panel / hover 表面角色；8dp 卡片、7dp 控件、细描边、粉紫强调色。
- 手机底部导航和 sw600dp 导航轨使用同一状态色；浏览列表加入轻量网格背景。
- 4:5 网格封面、左对齐双行标题、右上角选择角标与强调描边。
- 列数按 RecyclerView 的实际宽度计算，自动扣除导航轨、内边距并适应窗口尺寸变化。
- 手机详情的信息分层、固定底部继续阅读按钮，以及平板双栏详情；保留原业务回调。
- 搜索、分类、历史、设置、账户、登录、下载、评论与状态页面统一组件。
- 阅读器控制栏、目录和设置面板统一样式；正文阅读配色与阅读核心不变。竖向顶栏通过绘制顺序覆盖正文，不依靠高程。
- 下载页进入时应用当前主题，补齐相关繁体文案。
- 全量布局维度资源化，新增 `scripts/check-galisland.ps1` 检查 XML、旧组件引用、内联颜色/间距与导航一致性。

## 验证入口

```powershell
.\scripts\check-galisland.ps1
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug '-Pksp.incremental=false' --no-daemon
```

本次遇到 KSP 增量生成缺失 RxHttp 类，验证命令关闭增量生成后编译恢复；无需修改网络业务代码。离线缺少 JUnit/Hamcrest 时已通过联网解析补齐。

本地 SDK 路径的冒号转义已修正；`local.properties` 不纳入版本控制。没有扩充 lint baseline 来隐藏新增错误。

2026-09-10 本次结果：APK 构建成功；71 项单元测试通过；Lint 0 errors、149 warnings、2 hints（另有 282 项被既有 baseline 过滤）；视觉资源扫描通过。完整 Gradle 日志位于 `build/galisland-validation.log`，Lint 报告位于 `app/build/reports/lint-results-debug.html`。

## 设备验收限制

检测到 Android 16 平板（2136×3200，440dpi），但 debug APK 安装返回 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`。没有绕过该限制，也没有将旧版应用截图作为本次验证结果。

因此当前交付是已实现并经过构建/静态验证的代码；**像素观感、触控回归和截图矩阵仍待设备允许安装后验收**。

待验收矩阵：

| 页面 | 浅色/深色 | 手机 360/412dp | 平板 sw600dp |
| --- | --- | --- | --- |
| 首页、书架、批量选择 | 待截图 | 待检查 | 待检查 |
| 搜索、详情、设置、更多 | 待截图 | 待检查 | 待检查 |
| 横向/竖向阅读控制层 | 待截图 | 待检查 | 待检查 |

设备允许安装后检查主导航、搜索、卡片长按选择、详情继续阅读、主题切换、目录、进度和阅读设置；不需要执行删除书籍、投票、评论发送等写入操作来验证视觉。
