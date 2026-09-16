# GalIsland Android 视觉规范

参考基准：

- `E:\Tool\Galisland-UI-Template\styles\tokens.css`
- `E:\Tool\Galisland-UI-Template\styles\layout.css`
- `E:\Tool\Galisland-UI-Template\styles\components.css`

## 1. Surface 层级

页面必须先使用网格 Surface，再叠加局部组件表面：

| 语义 | Android token | 模板角色 |
| --- | --- | --- |
| 页面背景 | `colorBackground` + 40dp 网格 | `--bg-app` |
| 侧栏/导航 | 网格根 + 透明导航层；`giSurfaceSidebar` 仅用于抽屉 | `--bg-sidebar` / grid-first chrome |
| 工具栏/Tab | 网格根 + 透明控件层；保留描边与选中指示器 | `--bg-toolbar` / grid-first chrome |
| 阅读器 Chrome | `giSurfaceReaderChrome` | `--bg-panel` / `--bg-toolbar` |
| 输入框 | `giSurfaceInput` | `--bg-input` |
| 悬停 | `giSurfaceHover` | `--bg-panel-hover` |
| 网格线 | `giGridLine` | `--border` |

页面根容器使用以下之一：

- `LibrarySurfaceLayout`
- `GalislandGridConstraintLayout`
- `GalislandGridLinearLayout`

滚动容器默认保持透明，使网格底纹可以透出。

## 2. 尺寸与形状

- 网格单元：40dp
- 网格线：1dp
- 标签圆角：6dp
- 控件圆角：7dp
- 卡片圆角：8dp
- 大型面板圆角：10dp
- 卡片描边：1dp
- 卡片 elevation：0dp
- 小说封面比例：4:5

所有尺寸必须来自 `app/src/main/res/values/dimens.xml`，布局和 drawable 不得新增硬编码视觉尺寸。

## 3. 纯色填充规则

禁止：

- 页面根节点使用 `?attr/colorSurface` 覆盖网格
- 主页面使用不带网格的整页纯色背景
- 在布局中直接写入十六进制颜色
- 使用未映射的 Material3 默认组件样式

允许：

- 卡片、纸片面板、工具栏、导航栏和输入框使用语义 surface
- 封面占位背景
- 选中、错误和强调状态
- 阅读正文用户自定义背景色
- 阅读器底栏、状态栏、目录和设置面板

阅读正文是用户内容层，不强制叠加资料库网格；阅读器顶部/底部控制层必须使用 GalIsland Chrome token。

## 4. 阅读器 Chrome

- 顶部 Toolbar 使用 `giSurfaceToolbar`
- 底部 dock 和状态栏使用 `giSurfaceReaderChrome`
- 系统 inset 通过 `ReaderWindowInsetsCoordinator` 统一处理
- 隐藏底栏时整体移出父容器，不留下背景残片
- 状态行与菜单表面互斥，避免双层纯色带
- 阅读器沉浸模式隐藏色仍由用户阅读背景决定

## 5. 验证入口

```powershell
.\scripts\check-galisland.ps1
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug '-Pksp.incremental=false' --no-daemon
```

静态检查必须验证：XML、主题 token、页面网格根、reader Chrome token、非法页面级纯色背景和旧 Material 样式引用。
