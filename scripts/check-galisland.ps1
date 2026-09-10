$ErrorActionPreference = 'Stop'
$res = Join-Path $PSScriptRoot '../app/src/main/res'
$failures = [Collections.Generic.List[string]]::new()
foreach ($file in Get-ChildItem $res -Recurse -Filter '*.xml') {
    try { [void][xml](Get-Content -LiteralPath $file.FullName -Raw) }
    catch { $failures.Add("Invalid XML: $($file.Name)") }
}
foreach ($file in Get-ChildItem "$res/layout*/*.xml") {
    $text = Get-Content -LiteralPath $file.FullName -Raw
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
    $text = Get-Content "$res/$variant/activity_main.xml" -Raw
    foreach ($token in @('galisland_nav_item_icon', 'galisland_nav_item_text', 'NavigationBar.ActiveIndicator')) {
        if (!$text.Contains($token)) { $failures.Add("Missing navigation token $token in $variant") }
    }
}
if ($failures.Count) { $failures | ForEach-Object { Write-Error $_ -ErrorAction Continue }; exit 1 }
Write-Output 'GalIsland resource and visual consistency checks passed.'
