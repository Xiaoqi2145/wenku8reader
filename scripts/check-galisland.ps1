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
    if (!$text.Contains('com.cyh128.hikari_novel.ui.other.LibrarySurfaceLayout')) {
        $failures.Add("Page lost the GalIsland grid surface root: $rel")
    }
}
if ($failures.Count) { $failures | ForEach-Object { Write-Error $_ -ErrorAction Continue }; exit 1 }
Write-Output 'GalIsland resource and visual consistency checks passed.'