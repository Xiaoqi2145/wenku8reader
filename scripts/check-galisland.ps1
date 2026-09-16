$ErrorActionPreference = 'Stop'
$res = Join-Path $PSScriptRoot '../app/src/main/res'
$failures = [Collections.Generic.List[string]]::new()
foreach ($file in Get-ChildItem $res -Recurse -Filter '*.xml') {
    try { [void][xml](Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8) }
    catch { $failures.Add("Invalid XML: $($file.Name)") }
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
    'layout/activity_select_color.xml',
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

# Theme and reader Chrome tokens must remain semantic in both day and night resources.
foreach ($themeFile in @(
    "$res/values/themes.xml",
    "$res/values-night/themes.xml",
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
    if ($readerText -match 'colorSurfaceContainer') {
        $failures.Add("Reader code bypasses the semantic Chrome token: $([IO.Path]::GetFileName($readerCode))")
    }
    if (!$readerText.Contains('giSurfaceReaderChrome')) {
        $failures.Add("Reader code is missing the semantic Chrome token: $([IO.Path]::GetFileName($readerCode))")
    }
}
foreach ($readerSurface in @('bg_read_bottom_bar.xml', 'bg_read_status_bar.xml')) {
    $surfaceText = Get-Content -LiteralPath "$res/drawable/$readerSurface" -Raw -Encoding UTF8
    if (!$surfaceText.Contains('giSurfaceReaderChrome')) {
        $failures.Add("Reader surface bypasses the semantic Chrome token: $readerSurface")
    }
}
if ($failures.Count) { $failures | ForEach-Object { Write-Error $_ -ErrorAction Continue }; exit 1 }
Write-Output 'GalIsland resource and visual consistency checks passed.'