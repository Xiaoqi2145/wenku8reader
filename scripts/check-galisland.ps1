$ErrorActionPreference = 'Stop'
$res = Join-Path $PSScriptRoot '../app/src/main/res'
$failures = [Collections.Generic.List[string]]::new()
foreach ($file in Get-ChildItem $res -Recurse -Filter '*.xml') {
    try { [void][xml](Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8) }
    catch { $failures.Add("Invalid XML: $($file.Name)") }
}
$legacyMetricRefs = Get-ChildItem $res -Recurse -Filter '*.xml' | ForEach-Object {
    $text = Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8
    if ($text -match 'gi_metric_') { $_.FullName }
}
if ($legacyMetricRefs) {
    $legacyMetricRefs | ForEach-Object { $failures.Add("Legacy gi_metric token remains: $_") }
}
if (Test-Path "$res/values/galisland_layout_metrics.xml") {
    $failures.Add('Legacy galisland_layout_metrics.xml remains; use semantic dimens instead')
}
foreach ($file in Get-ChildItem "$res/layout*/*.xml") {
    $text = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
    $text = [regex]::Replace($text, '<!--[\s\S]*?-->', '')
    if ($text -match '@style/(Widget|TextAppearance)\.Material3') {
        $failures.Add("Unmapped Material style: $($file.Name)")
    }
    if ($text -match 'android:(textSize|padding\w*|layout_margin\w*)="[1-9][0-9]*(?:\.[0-9]+)?(?:dp|sp)"') {
        $failures.Add("Inline presentation dimension: $($file.Name)")
    }
    # Reader coach marks intentionally use white text over the dark overlay.
    if ($file.Name -ne 'view_horizontal_read_guide.xml' -and $text -match '="#[0-9a-fA-F]{6,8}"') {
        $failures.Add("Inline color: $($file.Name)")
    }
}
foreach ($variant in @('layout', 'layout-sw600dp')) {
    $text = Get-Content "$res/$variant/activity_main.xml" -Raw -Encoding UTF8
    foreach ($token in @('galisland_nav_item_icon', 'galisland_nav_item_text', 'NavigationBar.ActiveIndicator')) {
        if (!$text.Contains($token)) { $failures.Add("Missing navigation token $token in $variant") }
    }
}
$bottomBarLayout = Get-Content "$res/layout/view_read_bottom_bar.xml" -Raw -Encoding UTF8
if ($bottomBarLayout -match 'read_config') {
    $failures.Add('Obsolete duplicate reader settings entry remains in view_read_bottom_bar.xml')
}
foreach ($readerLayout in @('activity_horizontal_read.xml', 'activity_vertical_read.xml')) {
    $readerText = Get-Content "$res/layout/$readerLayout" -Raw -Encoding UTF8
    $includeCount = ([regex]::Matches($readerText, 'layout="@layout/view_read_bottom_bar"')).Count
    if ($includeCount -ne 1) {
        $failures.Add("Expected exactly one latest reader bottom bar in $readerLayout, found $includeCount")
    }
}
foreach ($readerCode in @(
    "$PSScriptRoot/../app/src/main/java/com/cyh128/hikari_novel/ui/read/horizontal/ReadActivity.kt",
    "$PSScriptRoot/../app/src/main/java/com/cyh128/hikari_novel/ui/read/vertical/ReadActivity.kt"
)) {
    if ((Get-Content -LiteralPath $readerCode -Raw -Encoding UTF8) -match '\.readConfig') {
        $failures.Add("Obsolete readConfig reference remains in $([IO.Path]::GetFileName($readerCode))")
    }
}
$gridPages = @(
    "$PSScriptRoot/../app/src/main/java/com/cyh128/hikari_novel/ui/main/bookshelf/BookshelfListFragment.kt",
    "$PSScriptRoot/../app/src/main/java/com/cyh128/hikari_novel/ui/main/home/recommend/RecommendFragment.kt",
    "$PSScriptRoot/../app/src/main/java/com/cyh128/hikari_novel/ui/main/home/category/CategoryContentFragment.kt",
    "$PSScriptRoot/../app/src/main/java/com/cyh128/hikari_novel/ui/main/home/ranking/RankingContentFragment.kt",
    "$PSScriptRoot/../app/src/main/java/com/cyh128/hikari_novel/ui/main/home/completion/CompletionFragment.kt",
    "$PSScriptRoot/../app/src/main/java/com/cyh128/hikari_novel/ui/main/home/search/SearchContentFragment.kt",
    "$PSScriptRoot/../app/src/main/java/com/cyh128/hikari_novel/ui/main/bookshelf/search/BookshelfSearchContentFragment.kt"
)
foreach ($gridPage in $gridPages) {
    $gridText = Get-Content -LiteralPath $gridPage -Raw -Encoding UTF8
    if (!$gridText.Contains('NovelGridLayoutManager')) {
        $failures.Add("Grid page is not using the responsive tablet layout manager: $([IO.Path]::GetFileName($gridPage))")
    }
}
# Browsing and secondary pages must root (or wrap) in the GalIsland grid surface
# so no page falls back to a flat solid background.
$gridSurfaceLayouts = @(
    'layout/activity_main.xml',
    'layout-sw600dp/activity_main.xml',
    'layout/activity_crash.xml',
    'layout/activity_splash.xml',
    'layout/fragment_guide.xml',
    'layout/fragment_logging_in.xml',
    'layout/fragment_login_error.xml',
    'layout/fragment_home.xml',
    'layout/fragment_bookshelf.xml',
    'layout/fragment_category.xml',
    'layout/fragment_ranking.xml',
    'layout/activity_novel_info.xml',
    'layout/fragment_novel_list.xml',
    'layout/fragment_recommend.xml',
    'layout/fragment_bookshelf_list.xml',
    'layout/fragment_bookshelf_search.xml',
    'layout/fragment_visit_history.xml',
    'layout/fragment_more.xml',
    'layout/activity_novel_info.xml',
    'layout/activity_setting.xml',
    'layout/activity_comment.xml',
    'layout/activity_user_bookshelf.xml',
    'layout/activity_bookshelf_search.xml',
    'layout/activity_search.xml',
    'layout/activity_login.xml',
    'layout/activity_account.xml',
    'layout/activity_about.xml',
    'layout/activity_download.xml',
    'layout/activity_download_detail.xml'
)
foreach ($rel in $gridSurfaceLayouts) {
    $text = Get-Content -LiteralPath (Join-Path $res $rel) -Raw -Encoding UTF8
    if ($text -notmatch 'com.cyh128.hikari_novel.ui.other.(LibrarySurfaceLayout|GalislandGridConstraintLayout|GalislandGridLinearLayout)') {
        $failures.Add("Page lost the GalIsland grid surface root: $rel")
    }
    if ($text -match '(?m)^\s*android:background="\?attr/colorSurface"') {
        $failures.Add("Page still uses a flat page-level colorSurface: $rel")
    }
    if ($rel -notmatch 'activity_(horizontal|vertical)_read.xml' -and $text -match 'android:background="\?attr/giSurfaceToolbar"') {
        $failures.Add("Browsing page chrome still paints a solid toolbar surface: $rel")
    }
    $pageBackgroundCount = ([regex]::Matches($text, 'android:background="\?android:attr/colorBackground"')).Count
    if ($pageBackgroundCount -gt 1) {
        $failures.Add("Page has an opaque child colorBackground covering its grid: $rel")
    }
}

# Theme and reader Chrome tokens must remain semantic.
# 注意：Base.Theme.HikariNovel 现在只在 values/themes.xml 维护。
# 原 values-night/themes.xml 的内容与 values 版逐项等价（纯重复定义），已移除；
# 昼夜差异由 values-night/colors_*.xml 的颜色变体承担，不依赖该文件。
foreach ($themeFile in @(
    "$res/values/themes.xml",
    "$res/values/galisland_surfaces.xml"
)) {
    $themeText = Get-Content -LiteralPath $themeFile -Raw -Encoding UTF8
    if (!$themeText.Contains('giSurfaceReaderChrome')) {
        $failures.Add("Missing reader Chrome token in $([IO.Path]::GetFileName($themeFile))")
    }
}
foreach ($readerCode in @(
    "$PSScriptRoot/../app/src/main/java/com/cyh128/hikari_novel/ui/read/horizontal/ReadActivity.kt",
    "$PSScriptRoot/../app/src/main/java/com/cyh128/hikari_novel/ui/read/vertical/ReadActivity.kt"
)) {
    $readerText = Get-Content -LiteralPath $readerCode -Raw -Encoding UTF8
    $readerName = [IO.Path]::GetFileName($readerCode)
    if ($readerText -match 'colorSurfaceContainer') {
        $failures.Add("Reader code bypasses the semantic Chrome token: $readerName")
    }
    # 阅读页 chrome 跟随阅读配色（P0-1）：颜色来源必须是运行时调色板而不是主题属性。
    # 断言必须落在「构造路径」上，而不是「出现过某个类型名」：曾经这里只查
    # ReaderChromePalette 字符串，于是把它改成 ReaderChromeTheme.paletteFor(...) 之后
    # 断言照样通过，等于没守住。paletteFor 是 accent 唯一的注入点。
    if (!$readerText.Contains('ReaderChromeTheme.paletteFor')) {
        $failures.Add("Reader chrome palette is not built through ReaderChromeTheme.paletteFor: $readerName")
    }
    if (!$readerText.Contains('ReaderChromeTheme')) {
        $failures.Add("Reader chrome is not applied through ReaderChromeTheme: $readerName")
    }
    if ($readerText -match '#[0-9A-Fa-f]{6}') {
        $failures.Add("Reader code hardcodes a chrome colour: $readerName")
    }
}
foreach ($readerSurface in @('bg_read_bottom_bar.xml', 'bg_read_status_bar.xml')) {
    $surfaceText = Get-Content -LiteralPath "$res/drawable/$readerSurface" -Raw -Encoding UTF8
    if (!$surfaceText.Contains('giSurfaceReaderChrome')) {
        $failures.Add("Reader surface bypasses the semantic Chrome token: $readerSurface")
    }
}

# ===================== 行为不变量（每一条都对应一次真实回归） =====================

# accent 是阅读页菜单唯一的品牌强调色。它曾在一次 chrome 重构里被静默抹掉：
# tintChromeContent 把 Material3 TextButton 默认的 colorPrimary 改写成了 onSurface，
# 而当时没有任何断言守着它。这里断言「有且只有一个注入点，并且真的被用上」。
$chromeThemePath = "$PSScriptRoot/../app/src/main/java/com/cyh128/hikari_novel/ui/read/common/ReaderChromeTheme.kt"
$chromeThemeText = Get-Content -LiteralPath $chromeThemePath -Raw -Encoding UTF8
if (!$chromeThemeText.Contains('fun paletteFor(')) {
    $failures.Add('ReaderChromeTheme no longer exposes paletteFor(); the accent lost its single injection point')
}
if (!$chromeThemeText.Contains('palette.accent')) {
    $failures.Add('ReaderChromeTheme no longer uses the accent role; the reader menu would lose its brand colour')
}

# 阅读页底色必须走共享的网格表面，不能退回纯色平铺。
foreach ($readerBg in @(
    "$PSScriptRoot/../app/src/main/java/com/cyh128/hikari_novel/ui/read/horizontal/ReadActivity.kt",
    "$PSScriptRoot/../app/src/main/java/com/cyh128/hikari_novel/ui/read/vertical/ReadActivity.kt",
    "$PSScriptRoot/../app/src/main/java/com/cyh128/hikari_novel/ui/read/vertical/ReadFragment.kt"
)) {
    if (!(Get-Content -LiteralPath $readerBg -Raw -Encoding UTF8).Contains('ReaderGridDrawable')) {
        $failures.Add("Reader page background is not the shared grid surface: $([IO.Path]::GetFileName($readerBg))")
    }
}

# 阅读页触感反馈必须走 ReaderHaptics，否则「震动反馈」开关会漏掉调用点。
foreach ($src in Get-ChildItem "$PSScriptRoot/../app/src/main/java/com/cyh128/hikari_novel/ui/read" -Recurse -Filter '*.kt') {
    if ((Get-Content -LiteralPath $src.FullName -Raw -Encoding UTF8) -match 'performHapticFeedback') {
        $failures.Add("Reader haptics bypass the configurable gate: $($src.Name)")
    }
}

# 小说卡片不得有纯色填充，否则底层网格表面透不出来。
# 正则要求 name 精确结束于 Novel，因此 Widget.GalIsland.CardView.Novel.Cover 不会被误匹配。
$novelCard = [regex]::Match(
    (Get-Content -LiteralPath "$res/values/galisland_styles.xml" -Raw -Encoding UTF8),
    '<style name="Widget\.GalIsland\.CardView\.Novel"[^>]*>([\s\S]*?)</style>'
)
if (!$novelCard.Success) {
    $failures.Add('Widget.GalIsland.CardView.Novel is missing; novel cards lost their shared style')
} elseif ($novelCard.Groups[1].Value -notmatch 'cardBackgroundColor">@android:color/transparent') {
    $failures.Add('Novel cards paint an opaque fill again; the grid surface cannot show through')
}

# 菜单开合的内容补偿必须走纯逻辑 + pre-draw：延迟到 post 会闪一帧，
# 且 pending 补偿会叠加（第二次基于未补偿的 scrollY）。
$readFragmentPath = "$PSScriptRoot/../app/src/main/java/com/cyh128/hikari_novel/ui/read/vertical/ReadFragment.kt"
$readFragmentText = Get-Content -LiteralPath $readFragmentPath -Raw -Encoding UTF8
if (!$readFragmentText.Contains('ReaderInsetCompensation')) {
    $failures.Add('Vertical reader inset compensation no longer uses ReaderInsetCompensation')
}
if ($readFragmentText -notmatch 'addOnPreDrawListener') {
    $failures.Add('Vertical reader compensates insets outside the pre-draw pass; the reflow would be visible for a frame')
}

if ($failures.Count) { $failures | ForEach-Object { Write-Error $_ -ErrorAction Continue }; exit 1 }
Write-Output 'GalIsland resource and visual consistency checks passed.'
