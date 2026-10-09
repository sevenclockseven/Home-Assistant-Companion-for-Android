# Syncs the Chinese (zh-rCN) translations of this fork from an official
# home-assistant/android release: downloads strings.zip, then routes every
# translated key into common/ or app/ based on which module owns the English
# source string. Fork-only keys are kept, keys without an English source are
# dropped as orphans.
#
# Usage:
#   ./scripts/sync-zh-translations.ps1                  # download latest official zip
#   ./scripts/sync-zh-translations.ps1 -OfficialTag 2026.10.1
#   ./scripts/sync-zh-translations.ps1 -OfficialStringsDir <dir>   # use an already extracted dir
param(
    [string]$OfficialTag = '',
    [string]$OfficialStringsDir = '',
    [string]$RepoRoot = (Split-Path $PSScriptRoot -Parent)
)
$ErrorActionPreference = 'Stop'

if (-not $OfficialStringsDir) {
    if (-not $OfficialTag) {
        throw 'Pass -OfficialTag <upstream release tag> or -OfficialStringsDir <extracted strings dir>.'
    }
    $zipUrl = "https://github.com/home-assistant/android/releases/download/$OfficialTag/strings.zip"
    $workDir = Join-Path ([System.IO.Path]::GetTempPath()) ("ha_strings_" + [System.IO.Path]::GetRandomFileName())
    $zipPath = Join-Path $workDir 'strings.zip'
    New-Item -ItemType Directory -Path $workDir -Force | Out-Null
    Write-Host "Downloading $zipUrl ..."
    Invoke-WebRequest -Uri $zipUrl -OutFile $zipPath
    Expand-Archive -Path $zipPath -DestinationPath $workDir -Force
    $OfficialStringsDir = $workDir
}

$offZhPath = Join-Path $OfficialStringsDir 'common/src/main/res/values-zh-rCN/strings.xml'
if (-not (Test-Path $offZhPath)) { throw "Official zh strings not found at $offZhPath" }

$offZh = [xml](Get-Content $offZhPath -Raw)
$curZh = [xml](Get-Content (Join-Path $RepoRoot 'common/src/main/res/values-zh-rCN/strings.xml') -Raw)
$comEn = [xml](Get-Content (Join-Path $RepoRoot 'common/src/main/res/values/strings.xml') -Raw)
$appEn = [xml](Get-Content (Join-Path $RepoRoot 'app/src/main/res/values/strings.xml') -Raw)

function Get-ResMap($xml) {
    $m = @{}
    foreach ($n in $xml.resources.ChildNodes) {
        if ($n.NodeType -ne [System.Xml.XmlNodeType]::Element) { continue }
        $k = $n.GetAttribute('name')
        if ($k) { $m[$k] = $n }
    }
    return $m
}
function Get-NsDecls($xml) {
    $decls = @()
    foreach ($a in $xml.root.Attributes) {
        if ($a.Prefix -eq 'xmlns' -or $a.Name -eq 'xmlns') { $decls += $a.OuterXml }
    }
    return $decls
}

$offMap = Get-ResMap $offZh
$curMap = Get-ResMap $curZh
$comKeys = Get-ResMap $comEn
$appKeys = Get-ResMap $appEn

$commonOut = New-Object System.Collections.ArrayList
$appOut = New-Object System.Collections.ArrayList
$keptFork = 0
$droppedOrphan = 0

foreach ($k in $offMap.Keys) {
    $el = $offMap[$k]
    if ($comKeys.ContainsKey($k)) { [void]$commonOut.Add($el) }
    elseif ($appKeys.ContainsKey($k)) { [void]$appOut.Add($el) }
    else { $droppedOrphan++ }
}
foreach ($k in $curMap.Keys) {
    if ($offMap.ContainsKey($k)) { continue }
    $el = $curMap[$k]
    if ($comKeys.ContainsKey($k)) { [void]$commonOut.Add($el); $keptFork++ }
    elseif ($appKeys.ContainsKey($k)) { [void]$appOut.Add($el); $keptFork++ }
    else { $droppedOrphan++ }
}

function Write-ResFile($path, $elements, $nsDecls) {
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append("<?xml version=`"1.0`" encoding=`"utf-8`"?>`n<resources")
    foreach ($d in $nsDecls) { [void]$sb.Append(" " + $d) }
    [void]$sb.Append(">`n")
    foreach ($el in $elements) { [void]$sb.Append("    " + $el.OuterXml + "`n") }
    [void]$sb.Append("</resources>`n")
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    Set-Content -Path $path -Value $sb.ToString() -Encoding utf8 -NoNewline
}

$nsCommon = Get-NsDecls $offZh
foreach ($d in (Get-NsDecls $curZh)) { if ($nsCommon -notcontains $d) { $nsCommon += $d } }

Write-ResFile (Join-Path $RepoRoot 'common/src/main/res/values-zh-rCN/strings.xml') $commonOut $nsCommon
Write-ResFile (Join-Path $RepoRoot 'app/src/main/res/values-zh-rCN/strings.xml') $appOut $nsCommon

$commonZhNames = @{}
foreach ($el in $commonOut) { $commonZhNames[$el.GetAttribute('name')] = $true }
$missing = @($comKeys.Keys | Where-Object { -not $commonZhNames.ContainsKey($_) })

Write-Output ("common zh output elements: {0}" -f $commonOut.Count)
Write-Output ("app zh output elements:    {0}" -f $appOut.Count)
Write-Output ("fork-only kept: {0}; orphans dropped: {1}" -f $keptFork, $droppedOrphan)
Write-Output ("residual missing (common en w/o zh): {0}" -f $missing.Count)
$missing | Select-Object -First 30
